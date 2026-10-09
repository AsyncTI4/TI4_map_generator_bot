package ti4.ai.runtime;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.actuation.AiActuator;
import ti4.ai.actuation.AiActuator.PressTarget;
import ti4.ai.actuation.AiActuator.SeatIdentity;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.FactionBrain;
import ti4.ai.brain.FactionBrainRegistry;
import ti4.ai.fallback.AiConfusionService;
import ti4.ai.fallback.DelegatedPress;
import ti4.ai.perception.AiPerception;
import ti4.ai.perception.AiPerception.Snapshot;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.profile.AiProfile;
import ti4.ai.runtime.AiSeatState.DelegationRequest;
import ti4.ai.runtime.AiStallDetector.WaitReason;
import ti4.ai.seat.AiSeatService;
import ti4.ai.selfplay.RefereeRules;
import ti4.ai.selfplay.RefereeRules.RefereePress;
import ti4.ai.tactical.CombatRules;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.service.game.GameUndoNameService;

@UtilityClass
class AiTickRunner {

    private static final long RETRY_DELAY_MILLIS = 30_000L;
    private static final long HEALTH_WINDOW_MILLIS = 600_000L;
    private static final int GAME_FAILURE_LIMIT = 3;
    private static final int GLOBAL_FAILURE_LIMIT = 10;
    private static final Deque<Long> RECENT_FAILURES = new ArrayDeque<>();

    private record SeatTick(
            AiLane lane,
            AiSeatState state,
            Game game,
            Player seat,
            SeatIdentity identity,
            AiProfile profile,
            List<AiPrompt> prompts,
            boolean selfPlay,
            long now) {}

    private record SeatResult(boolean acted, long wakeAt) {
        static SeatResult act() {
            return new SeatResult(true, 0);
        }

        static SeatResult wakeAt(long at) {
            return new SeatResult(false, at);
        }
    }

    static void tick(AiLane lane) {
        tick(lane, System.currentTimeMillis());
    }

    static void tick(AiLane lane, long now) {
        if (!AiRuntime.mayMutate()) {
            lane.markDirty(now + RETRY_DELAY_MILLIS);
            return;
        }
        ManagedGame managed = GameManager.getManagedGame(lane.getGameName());
        if (managed == null || managed.isHasEnded()) {
            AiRuntime.forget(lane);
            return;
        }
        Game game = managed.getGame();
        List<Player> seats = AiSeats.activeAiSeats(game);
        if (seats.isEmpty()) {
            AiRuntime.forget(lane);
            return;
        }
        lane.retainSeats(seats);
        if (lane.observeUndoIndex(latestUndoIndex(game.getName()))) lane.forgetAfterUndo();
        boolean selfPlay = AiSeats.isSelfPlay(game);
        if (reachedVictoryPoints(game)) {
            if (selfPlay && lane.markGameOverAnnounced()) announceGameOver(game, false);
            return;
        }

        DelegatedPress delegated = lane.pollDelegatedPress();
        if (delegated != null) {
            pressDelegated(lane, game, delegated, now);
            lane.markDirty(now + AiSettings.DEBOUNCE.toMillis());
            return;
        }
        if (now - lane.lastActionAt() < AiSettings.MIN_GAP_BETWEEN_ACTIONS.toMillis()) {
            lane.markDirty(lane.lastActionAt() + AiSettings.MIN_GAP_BETWEEN_ACTIONS.toMillis());
            return;
        }

        Snapshot snapshot = AiPerception.read(game, seats);
        List<AiPrompt> refereePrompts =
                snapshot.publicPrompts(RefereeRules.anyStep().or(button -> "gameEnd".equals(button.handlerId())));
        if (selfPlay && RefereeRules.trackExhausted(game) && RefereeRules.offersGameEnd(refereePrompts)) {
            if (lane.markGameOverAnnounced()) announceGameOver(game, true);
            return;
        }
        long wakeAt = Long.MAX_VALUE;
        for (Player seat : lane.rotation(seats)) {
            SeatResult result = runSeat(lane, game, seat, snapshot, selfPlay, now);
            if (result.acted()) {
                lane.markDirty(now + AiSettings.DEBOUNCE.toMillis());
                return;
            }
            if (result.wakeAt() > 0) wakeAt = Math.min(wakeAt, result.wakeAt());
        }
        if (selfPlay && referee(lane, game, seats, refereePrompts, now)) {
            lane.markDirty(now + AiSettings.DEBOUNCE.toMillis());
            return;
        }
        if (selfPlay && now - game.getLastModifiedDate() < AiSettings.DORMANT_AFTER.toMillis()) {
            wakeAt = Math.min(wakeAt, now + AiSettings.refereeDelay(game).toMillis());
        }
        if (wakeAt != Long.MAX_VALUE) lane.markDirty(wakeAt);
    }

    static boolean reachedVictoryPoints(Game game) {
        return game.getVp() > 0 && game.getHighestScore() >= game.getVp();
    }

    static boolean isCoolingDown(AiLane lane, long now) {
        return !isHealthy(lane, now);
    }

    private static int latestUndoIndex(String gameName) {
        try {
            List<Integer> undoNumbers = GameUndoNameService.getSortedUndoNumbers(gameName);
            return undoNumbers.isEmpty() ? -1 : undoNumbers.getLast();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static SeatIdentity identityOf(Player seat) {
        return new SeatIdentity(seat.getUserID(), seat.getFaction(), seat.getUserName(), seat.getCardsInfoThreadID());
    }

    private static SeatResult runSeat(
            AiLane lane, Game game, Player seat, Snapshot snapshot, boolean selfPlay, long now) {
        AiProfile profile = AiProfile.of(seat);
        Optional<FactionBrain> brain = FactionBrainRegistry.forBrainId(profile.brainId());
        Set<String> windowPrefixes =
                brain.map(FactionBrain::publicWindowHandlerPrefixes).orElse(Set.of());
        List<AiPrompt> prompts = snapshot.promptsFor(seat, windowPrefixes);
        SeatTick tick = new SeatTick(
                lane, lane.seat(seat.getUserID()), game, seat, identityOf(seat), profile, prompts, selfPlay, now);
        boolean canAct = brain.isPresent() && !profile.isPaused() && mayActOnItsOwn(tick);
        if (canAct) AiSeatService.ensureTradePreferences(seat.getUserID());
        AiDecision decision = canAct
                ? decide(
                        brain.get(),
                        new AiTurnContext(
                                game,
                                seat,
                                profile,
                                prompts,
                                tick.state().pressedKeys(),
                                tick.state().memory(),
                                now))
                : AiDecision.idle();
        return switch (decision) {
            case AiDecision.Press press -> handlePress(tick, press);
            case AiDecision.Unsure unsure -> fallBack(tick, unsure.prompt(), unsure.reason(), false);
            case AiDecision.Wait wait -> {
                if (!tick.state().hasDelegationRequest()) yield SeatResult.wakeAt(wait.untilMillis());
                SeatResult idle = handleIdle(tick);
                yield idle.acted() ? idle : SeatResult.wakeAt(wait.untilMillis());
            }
            case AiDecision.Idle idle -> handleIdle(tick);
            case AiDecision.Announce announce -> {
                if (AiRuntime.mayMutate()) AiConfusionService.notice(tick.game(), announce.text());
                yield SeatResult.act();
            }
        };
    }

    private static boolean mayActOnItsOwn(SeatTick tick) {
        return AiSettings.isEnabled()
                && isHealthy(tick.lane(), tick.now())
                && underActionCap(tick.game(), tick.state(), tick.now());
    }

    private static boolean underActionCap(Game game, AiSeatState state, long now) {
        return state.actionsInLastHour(now) < AiSettings.maxActionsPerHour(game);
    }

    private static AiDecision decide(FactionBrain brain, AiTurnContext context) {
        try {
            return brain.decide(context);
        } catch (RuntimeException e) {
            BotLogger.error(
                    "AI brain failed for " + context.faction() + " in "
                            + context.game().getName(),
                    e);
            return AiDecision.idle();
        }
    }

    private static SeatResult handlePress(SeatTick tick, AiDecision.Press press) {
        String attemptKey = AiTurnContext.pressKey(press.prompt(), press.button()) + "#"
                + AiProgress.fingerprint(tick.game(), tick.seat());
        int attempts = tick.state().attemptsFor(attemptKey);
        if (attempts >= AiSettings.MAX_ATTEMPTS_PER_PROMPT) {
            if (attempts == AiSettings.MAX_ATTEMPTS_PER_PROMPT) {
                recordFailure(tick.lane(), tick.now());
                tick.state().recordAttempt(attemptKey);
            }
            return fallBack(tick, press.prompt(), "its choice made no progress", false);
        }
        tick.state().recordAttempt(attemptKey);
        if (pressAsSeat(tick, press.prompt(), press.button())) {
            tick.state().clearFallbacks();
        } else {
            for (int i = 0; i < AiSettings.MAX_ATTEMPTS_PER_PROMPT; i++)
                tick.state().recordAttempt(attemptKey);
        }
        return SeatResult.act();
    }

    private static boolean pressAsSeat(SeatTick tick, AiPrompt prompt, PromptButton button) {
        if (!AiRuntime.mayMutate()) return true;
        tick.lane().recordAction(tick.seat().getUserID(), tick.now());
        AiActuator.Result result = AiActuator.press(tick.identity(), target(prompt, button));
        switch (result.outcome()) {
            case PRESSED -> {
                tick.state().recordPressed(AiTurnContext.pressKey(prompt, button), tick.now());
                return true;
            }
            case STALE -> {
                return true;
            }
            default -> {
                recordFailure(tick.lane(), tick.now());
                BotLogger.warning(tick.seat().getUserName() + " could not press `" + button.customId() + "` in "
                        + tick.game().getName() + ": " + result.detail());
                return false;
            }
        }
    }

    private static SeatResult handleIdle(SeatTick tick) {
        AiSeatState state = tick.state();
        List<WaitReason> reasons = AiStallDetector.waitReasons(tick.game(), tick.seat(), tick.prompts());
        String described = AiStallDetector.describe(reasons);
        long stallAge = state.stallAge(described, tick.now());
        DelegationRequest request = state.consumeDelegationRequest();
        boolean forced =
                request == DelegationRequest.EXPLICIT || (request == DelegationRequest.GAME_WIDE && !reasons.isEmpty());
        if (reasons.isEmpty() && !forced) return SeatResult.wakeAt(0);
        long threshold = AiSettings.stallThreshold(tick.game()).toMillis();
        if (stallAge < threshold && !forced) return SeatResult.wakeAt(tick.now() + threshold - stallAge);
        Optional<AiPrompt> blocking = tick.prompts().stream()
                .filter(prompt -> !prompt.enabledButtons().isEmpty())
                .filter(prompt -> !state.wasDelegated(prompt.key()))
                .filter(prompt -> prompt.enabledButtons().stream()
                        .noneMatch(button -> state.hasPressed(AiTurnContext.pressKey(prompt, button))))
                .filter(prompt -> forced && reasons.isEmpty()
                        ? !prompt.isHidden() && addressedTo(prompt, tick.seat())
                        : reasons.stream().anyMatch(reason -> reason.related().test(prompt)))
                .max(Comparator.comparingLong(AiPrompt::createdAtMillis));
        String why = reasons.isEmpty() ? "a player asked it to hand over its choice" : "waiting on " + described;
        state.restartStall(tick.now());
        if (blocking.isPresent()) return fallBack(tick, blocking.get(), why, forced);
        if (forced || state.markStuckAnnounced()) {
            AiConfusionService.announceStuck(
                    tick.game(), tick.seat(), reasons.isEmpty() ? "nothing it can hand over" : described);
            return SeatResult.act();
        }
        return SeatResult.wakeAt(tick.now() + threshold);
    }

    private static SeatResult fallBack(SeatTick tick, AiPrompt prompt, String reason, boolean requested) {
        AiSeatState state = tick.state();
        state.restartStall(tick.now());
        if (state.wasDelegated(prompt.key())) return SeatResult.wakeAt(0);
        if (requested || state.consumeDelegationRequest() != DelegationRequest.NONE) {
            state.clearFallbacks();
        } else {
            switch (state.recordFallback(reason + "@" + turnKey(tick.game()), AiSettings.MAX_REPEATED_FALLBACKS)) {
                case STOP_AND_ANNOUNCE -> {
                    AiConfusionService.announceLoop(
                            tick.game(), tick.seat(), reason, AiSettings.MAX_REPEATED_FALLBACKS + 1, tick.selfPlay());
                    return SeatResult.act();
                }
                case STOPPED -> {
                    return SeatResult.wakeAt(0);
                }
                case CHOOSE -> {}
            }
        }
        int round = tick.game().getRound();
        if (!state.canDelegate(round, AiSettings.MAX_DELEGATIONS_PER_ROUND)
                || !tick.lane().recordGameDelegation(round, AiSettings.MAX_GAME_DELEGATIONS_PER_ROUND)) {
            if (state.markStuckAnnounced()) {
                AiConfusionService.announceStuck(tick.game(), tick.seat(), reason);
                return SeatResult.act();
            }
            return SeatResult.wakeAt(0);
        }
        state.recordDelegation(prompt.key());
        if (tick.selfPlay() && !tick.profile().isPaused()) return chooseWithoutHumans(tick, prompt, reason);
        if (!prompt.isHidden()) {
            if (!AiConfusionService.delegate(tick.game(), tick.seat(), prompt, reason, offerable(tick, prompt))) {
                AiConfusionService.announceStuck(tick.game(), tick.seat(), reason);
            }
            return SeatResult.act();
        }
        Optional<PromptButton> safeChoice = AiConfusionService.safeDefault(prompt);
        if (safeChoice.isEmpty() || tick.profile().isPaused() || !mayActOnItsOwn(tick)) {
            AiConfusionService.announceStuck(tick.game(), tick.seat(), "a private choice");
            return SeatResult.act();
        }
        if (pressAsSeat(tick, prompt, safeChoice.get())) {
            AiConfusionService.announcePrivateChoice(tick.game(), tick.seat());
        }
        return SeatResult.act();
    }

    private static String turnKey(Game game) {
        Date turnStart = game.getLastActivePlayerChange();
        return game.getActivePlayerID() + "@" + (turnStart == null ? 0 : turnStart.getTime());
    }

    private static boolean addressedTo(AiPrompt prompt, Player seat) {
        return prompt.buttons().stream().anyMatch(button -> button.isOwnedBy(seat.getFaction()));
    }

    private static Predicate<String> offerable(SeatTick tick, AiPrompt prompt) {
        return customId -> prompt.buttons().stream()
                .filter(button -> button.customId().equals(customId))
                .findFirst()
                .map(button ->
                        (button.isUnowned() || button.isOwnedBy(tick.seat().getFaction()))
                                && (!button.handlerId().startsWith("combatRoll_")
                                        || CombatRules.isRollFor(tick.game(), tick.seat(), tick.prompts(), button)))
                .orElse(true);
    }

    private static SeatResult chooseWithoutHumans(SeatTick tick, AiPrompt prompt, String reason) {
        Optional<PromptButton> choice = AiConfusionService.selfPlayChoice(prompt)
                .filter(button -> offerable(tick, prompt).test(button.customId()));
        if (choice.isEmpty() || !mayActOnItsOwn(tick)) {
            AiConfusionService.announceStuck(tick.game(), tick.seat(), reason);
            return SeatResult.act();
        }
        if (pressAsSeat(tick, prompt, choice.get())) {
            AiConfusionService.announceSelfPlayChoice(tick.game(), tick.seat(), choice.get(), reason);
        }
        return SeatResult.act();
    }

    private static boolean referee(
            AiLane lane, Game game, List<Player> seats, List<AiPrompt> refereePrompts, long now) {
        if (!AiSettings.isEnabled() || !isHealthy(lane, now)) return false;
        if (now - lane.lastActionAt() < AiSettings.refereeDelay(game).toMillis()) return false;
        boolean seatHeldBack = seats.stream()
                .anyMatch(seat ->
                        AiProfile.of(seat).isPaused() || !underActionCap(game, lane.seat(seat.getUserID()), now));
        if (seatHeldBack) return false;
        int state = RefereeRules.stateFingerprint(game);
        Optional<RefereePress> press =
                RefereeRules.next(game, seats, refereePrompts, pressKey -> lane.refereeMayPress(pressKey, state), now);
        if (press.isEmpty()) return false;
        Player seat = press.get().seat();
        SeatTick tick = new SeatTick(
                lane,
                lane.seat(seat.getUserID()),
                game,
                seat,
                identityOf(seat),
                AiProfile.of(seat),
                List.of(),
                true,
                now);
        lane.recordRefereePress(
                AiTurnContext.pressKey(press.get().prompt(), press.get().button()), state);
        if (pressAsSeat(tick, press.get().prompt(), press.get().button())) {
            BotLogger.info(seat.getUserName() + " moved " + game.getName() + " along: "
                    + press.get().reason());
        }
        return true;
    }

    private static void pressDelegated(AiLane lane, Game game, DelegatedPress press, long now) {
        Player seat = game.getPlayer(press.choice().seatId());
        if (!AiSeats.isActiveAiSeat(seat)) {
            AiConfusionService.resolve(press, "the AI", false);
            return;
        }
        lane.recordAction(seat.getUserID(), now);
        Optional<String> customId = originalCustomId(press);
        boolean pressed = customId.isPresent()
                && AiRuntime.mayMutate()
                && AiActuator.press(
                                identityOf(seat),
                                new PressTarget(
                                        press.choice().channelId(),
                                        press.choice().messageId(),
                                        customId.get()))
                        .pressed();
        if (pressed) lane.seat(seat.getUserID()).recordPressed("delegated|" + press.delegationMessageId(), now);
        AiConfusionService.resolve(press, seat.getRepresentationNoPing(), pressed);
    }

    private static Optional<String> originalCustomId(DelegatedPress press) {
        if (JdaService.jda == null) return Optional.empty();
        GuildMessageChannel channel = JdaService.jda.getChannelById(
                GuildMessageChannel.class, press.choice().channelId());
        if (channel == null) return Optional.empty();
        Optional<Message> message = AiActuator.retrieve(channel, press.choice().messageId());
        if (message.isEmpty()) return Optional.empty();
        List<Button> buttons = message.get().getComponentTree().findAll(Button.class);
        int index = press.choice().index();
        if (index < 0 || index >= buttons.size()) return Optional.empty();
        String customId = buttons.get(index).getCustomId();
        if (customId == null || !press.choice().matches(customId)) return Optional.empty();
        return Optional.of(customId);
    }

    private static void announceGameOver(Game game, boolean trackExhausted) {
        int best = game.getHighestScore();
        String leaders = String.join(
                ", ",
                game.getRealPlayers().stream()
                        .filter(player -> player.getTotalVictoryPoints() == best)
                        .map(Player::getRepresentationNoPing)
                        .toList());
        String why = trackExhausted
                ? "Every objective has been revealed. " + leaders + " leads with " + best + " victory points"
                : leaders + " reached " + game.getVp() + " victory points";
        AiConfusionService.notice(
                game, "🏁 " + why + ", so the AI players have stopped. Use `/game end` to finish the game.");
    }

    private static PressTarget target(AiPrompt prompt, PromptButton button) {
        return new PressTarget(prompt.channelId(), prompt.messageId(), button.customId());
    }

    private static void recordFailure(AiLane lane, long now) {
        lane.recordFailure(now);
        synchronized (RECENT_FAILURES) {
            RECENT_FAILURES.addLast(now);
            prune(now);
            if (RECENT_FAILURES.size() == GLOBAL_FAILURE_LIMIT) {
                BotLogger.warning(
                        "AI players stopped acting on their own in every game after repeated button" + " failures.");
            }
        }
    }

    private static boolean isHealthy(AiLane lane, long now) {
        if (lane.recentFailures(now) >= GAME_FAILURE_LIMIT) return false;
        synchronized (RECENT_FAILURES) {
            prune(now);
            return RECENT_FAILURES.size() < GLOBAL_FAILURE_LIMIT;
        }
    }

    private static void prune(long now) {
        while (!RECENT_FAILURES.isEmpty() && now - RECENT_FAILURES.peekFirst() > HEALTH_WINDOW_MILLIS) {
            RECENT_FAILURES.removeFirst();
        }
    }
}
