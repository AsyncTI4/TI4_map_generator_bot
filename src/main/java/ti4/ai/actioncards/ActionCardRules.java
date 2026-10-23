package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.MoveBoost;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.secrets.SpyNetwork;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.service.actioncard.SabotageService;
import ti4.service.agenda.IsPlayerElectedService;

@UtilityClass
public class ActionCardRules {

    private static final String REF_KEY = "acResolveRef|";
    private static final String FLANK_SPEED_BOOST = "flankspeedBoost";
    private static final String FIELD = "~";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String SUMMIT_PRESET = "resolvePreassignment_Summit";
    private static final String SUMMIT_STORED = "Summit";
    private static final long ANNOUNCEMENT_MILLIS = 5 * 60_000L;
    private static final long PROMPT_MILLIS = 2 * 60_000L;
    private static final long CONFIRM_MILLIS = 60_000L;
    private static final long HOLD_MILLIS = 60_000L;
    private static final long SETTLE_MILLIS = 10_000L;
    private static final long OTHER_CANCEL_GRACE_MILLIS = 5 * 60_000L;
    private static final long WINDOW_CEILING_MILLIS = 24 * 60 * 60_000L;
    private static final Set<String> CENSURE = Set.of("censure", "absol_censure");

    public static Set<String> targetPrefixes() {
        return AiActionCards.targetPrefixes();
    }

    public static Optional<AiDecision> playBeforePassing(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        String key = PlayMemory.turnKey(context);
        if (context.memory().has(key) || !mayPlay(game, seat) || keepsSpyNetworkCards(seat)) return Optional.empty();
        return ActionCardValue.bestComponentPlay(game, seat, PlayMoment.BEFORE_PASSING)
                .flatMap(best -> startPlay(context, key, best.alias(), PlayMoment.BEFORE_PASSING));
    }

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        String key = PlayMemory.turnKey(context);
        if (context.memory().has(key) || !mayPlay(game, seat) || keepsSpyNetworkCards(seat)) return Optional.empty();
        Optional<ActionCardValue.Candidate> best = ActionCardValue.bestNewComponentPlay(game, seat);
        if (best.isEmpty()
                || (!best.get().urgent()
                        && tacticalActionBeats(context, best.get().value()))) {
            return Optional.empty();
        }
        return startPlay(context, key, best.get().alias(), PlayMoment.IN_TURN);
    }

    private static boolean tacticalActionBeats(AiTurnContext context, double actionValue) {
        Optional<TacticalPlan> plan = TacticalRules.rememberedPlan(context).or(() -> {
            Optional<TacticalPlan> computed = TacticalPlanner.best(context.game(), context.seat());
            computed.ifPresent(found -> TacticalRules.remember(context, found));
            return computed;
        });
        return plan.isPresent() && plan.get().score() >= actionValue;
    }

    public static Optional<AiDecision> afterActivation(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (context.isActivePlayer() || AiSettings.usesLegacyActionCards(seat)) return Optional.empty();
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())
                || game.getActiveSystem().isBlank()) {
            return Optional.empty();
        }
        Tile activated = game.getTileByPosition(game.getActiveSystem());
        String key = PlayMemory.reactionKey(context);
        if (activated == null || context.memory().has(key)) return Optional.empty();
        if (!mayPlay(game, seat) || keepsSpyNetworkCards(seat)) return Optional.empty();
        Optional<ActionCardValue.Candidate> best =
                ActionCardValue.bestReactionPlay(game, seat, Window.AFTER_ACTIVATION);
        return best.flatMap(card -> startPlay(context, key, card.alias(), PlayMoment.IN_TURN));
    }

    public static Optional<AiDecision> flankSpeed(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!context.isActivePlayer() || AiSettings.usesLegacyActionCards(seat)) return Optional.empty();
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())
                || game.getActiveSystem().isBlank()) {
            return Optional.empty();
        }
        if (!game.getStoredValue(FLANK_SPEED_BOOST).isEmpty() || !TacticalRules.activatedThisTurn(context)) {
            return Optional.empty();
        }
        String key = PlayMemory.turnKey(context);
        if (context.memory().has(key) || !mayPlay(game, seat)) return Optional.empty();
        Optional<String> alias = MoveBoost.flankSpeedCard(seat);
        boolean needed = TacticalRules.rememberedPlan(context)
                .filter(plan -> plan.target().equals(game.getActiveSystem()))
                .filter(plan -> TacticalPlanner.needsFlankSpeed(game, seat, plan))
                .isPresent();
        if (alias.isEmpty() || !needed) return Optional.empty();
        return startPlay(context, key, alias.get(), PlayMoment.IN_TURN);
    }

    public static Optional<AiDecision> masterPlan(AiTurnContext context, boolean secondActionIsWorthIt) {
        Game game = context.game();
        Player seat = context.seat();
        String key = PlayMemory.masterPlanKey(context);
        if (!secondActionIsWorthIt
                || context.memory().has(key)
                || AiSettings.usesLegacyActionCards(seat)
                || !mayPlay(game, seat)
                || keepsSpyNetworkCards(seat)) {
            return Optional.empty();
        }
        return seat.getActionCards().keySet().stream()
                .filter(alias -> AiActionCards.of(alias)
                        .filter(card -> card.window() == Window.AFTER_ACTION)
                        .isPresent())
                .sorted()
                .findFirst()
                .flatMap(alias -> startPlay(context, key, alias, PlayMoment.IN_TURN));
    }

    public static Optional<AiDecision> holdOwnTurn(AiTurnContext context) {
        if (!context.isActivePlayer()) return Optional.empty();
        Optional<CardPlay> play = Stream.of(PlayMemory.turnKey(context), PlayMemory.masterPlanKey(context))
                .map(key -> context.memory().get(key).flatMap(CardPlay::decode))
                .flatMap(Optional::stream)
                .filter(CardPlay::active)
                .findFirst();
        if (play.isEmpty()) return Optional.empty();
        String title = SabotageWindow.title(play.get().alias());
        return Optional.of(new AiDecision.Wait(context.now() + HOLD_MILLIS, "the " + title + " action card"));
    }

    public static Optional<AiDecision> continuePlay(AiTurnContext context) {
        CardRecovery.recover(context);
        for (String key : PlayMemory.activeKeys(context)) {
            Optional<AiDecision> decision = advance(context, key);
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    private static boolean censured(Game game, Player seat) {
        for (String law : CENSURE) {
            if (IsPlayerElectedService.isPlayerElected(game, seat, law)) return true;
        }
        return false;
    }

    private static long settleMillis(Game game, Player seat, CardPlay play) {
        if (play.windowExpected()) return SETTLE_MILLIS;
        boolean otherCancels = game.getRealPlayers().stream()
                .filter(player -> player != seat)
                .anyMatch(player -> SabotageService.couldUseInstinctTraining(player)
                        || SabotageService.couldUseWatcherMech(player, game));
        return otherCancels ? OTHER_CANCEL_GRACE_MILLIS : SETTLE_MILLIS;
    }

    public static Optional<AiDecision> presetSummit(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.getActionCards().containsKey(ActionCardValue.SUMMIT) || !mayPlay(game, seat)) return Optional.empty();
        String key = PlayMemory.summitKey();
        boolean tracked = context.memory()
                .get(key)
                .flatMap(CardPlay::decode)
                .filter(CardPlay::active)
                .isPresent();
        if (tracked) return Optional.empty();
        CardPlay preset =
                new CardPlay(Stage.PRESET, context.now(), context.now(), ActionCardValue.SUMMIT, "", false, 0, "");
        if (game.getStoredValue(SUMMIT_STORED).contains(seat.getFaction())) {
            PlayMemory.store(context, key, preset);
            return Optional.empty();
        }
        if (CommandTokenPolicy.gainWithinReinforcements(game, seat) < 1) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.firstEnabled(
                    candidate -> candidate.isUnowned() && SUMMIT_PRESET.equals(candidate.handlerId()));
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                PlayMemory.store(context, key, preset);
                return Optional.of(AiDecision.press(prompt, button.get(), "pre-play Summit for 2 command tokens"));
            }
        }
        return Optional.empty();
    }

    public static boolean mayPlayNow(Game game, Player seat) {
        return !AiSettings.usesLegacyActionCards(seat) && mayPlay(game, seat) && !keepsSpyNetworkCards(seat);
    }

    static boolean mayPlay(Game game, Player seat) {
        if (censured(game, seat) || ButtonHelper.isPlayerOverLimit(game, seat)) return false;
        Player active = game.getActivePlayer();
        return !(seat.isPassed() && active != null && active.hasTech("tp"));
    }

    public static boolean holdsPlayableStrategize(Game game, Player seat) {
        return StrategizeCard.playable(game, seat);
    }

    public static boolean strategizeSecondaryWorthAToken(Game game, Player seat) {
        return StrategizeCard.secondaryWorthAToken(game, seat);
    }

    public static double frontlineValue(Game game, Player seat, String planetName) {
        return EconomyTargets.frontlineValue(game, seat, planetName);
    }

    private static boolean keepsSpyNetworkCards(Player seat) {
        return SpyNetwork.holds(seat) && seat.getAcCount() <= SpyNetwork.CARDS;
    }

    static Optional<AiDecision> startPlay(AiTurnContext context, String key, String alias, PlayMoment moment) {
        Optional<AiCard> card = AiActionCards.of(alias);
        if (card.isEmpty()) return Optional.empty();
        Plan plan = card.get().planFor(context.game(), context.seat(), moment).orElse(Plan.of(0));
        return startPlay(context, key, alias, plan);
    }

    static Optional<AiDecision> startPlay(AiTurnContext context, String key, String alias, Plan plan) {
        Integer index = context.seat().getActionCards().get(alias);
        Optional<AiCard> card = AiActionCards.of(alias);
        if (index == null || card.isEmpty()) return Optional.empty();
        String title = SabotageWindow.title(alias);
        Optional<Match> play = ActionCardHand.playButton(
                context, index, card.get().playSuffix().apply(context.game()), Long.MIN_VALUE);
        if (play.isPresent()) {
            PlayMemory.store(context, key, played(context, alias, plan.target()));
            PlayMemory.rememberAnnouncement(context, key, announcementOf(context, title, plan));
            return Optional.of(play.get().press("play " + title));
        }
        Optional<AiDecision> refresh = ActionCardHand.requestHand(context, key);
        if (refresh.isPresent() || !card.get().isComponentAction()) return refresh;
        return openMenu(context, key, alias, plan, title);
    }

    private static Optional<AiDecision> openMenu(
            AiTurnContext context, String key, String alias, Plan plan, String title) {
        if (!context.isActivePlayer()) return Optional.empty();
        Optional<Match> menu = Prompts.owned(Prompts.thisTurn(context), context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        PlayMemory.store(
                context,
                key,
                new CardPlay(Stage.MENU, context.now(), context.now(), alias, "", false, 0, plan.target()));
        PlayMemory.rememberAnnouncement(context, key, announcementOf(context, title, plan));
        return Optional.of(menu.get().press("open the component actions to play " + title));
    }

    private static String announcementOf(AiTurnContext context, String title, Plan plan) {
        if (plan.announcement().isBlank()) return "";
        return "🤖 " + context.seat().getRepresentationNoPing() + " plays _" + title + "_ " + plan.announcement();
    }

    private static CardPlay played(AiTurnContext context, String alias, String target) {
        int plays = context.game().getGameStats().getActionCardPlays().size();
        boolean window = SabotageWindow.expected(context.game(), context.seat(), alias);
        return new CardPlay(Stage.PLAYED, context.now(), context.now(), alias, "", window, plays, target);
    }

    private static Optional<AiDecision> advance(AiTurnContext context, String key) {
        Optional<CardPlay> stored = context.memory().get(key).flatMap(CardPlay::decode);
        if (stored.isEmpty() || !stored.get().active()) return Optional.empty();
        CardPlay play = stored.get();
        if (key.startsWith(PlayMemory.COMBAT_PREFIX) && CombatCardRules.outdated(context, key)) {
            return stop(context, key, play, Stage.CANCELED);
        }
        return switch (play.stage()) {
            case MENU -> fromMenu(context, key, play);
            case PRESET -> afterPreset(context, key, play);
            case PLAYED -> afterPlay(context, key, play);
            case WINDOW -> duringWindow(context, key, play);
            case RESOLVE -> resolve(context, key, play);
            case TARGET -> target(context, key, play);
            case DONE, CANCELED -> Optional.empty();
        };
    }

    private static Optional<AiDecision> move(AiTurnContext context, String key, CardPlay next) {
        PlayMemory.store(context, key, next);
        return next.active() ? advance(context, key) : Optional.empty();
    }

    private static Optional<AiDecision> stop(AiTurnContext context, String key, CardPlay play, Stage stage) {
        PlayMemory.moveTo(context, key, play, stage);
        return Optional.empty();
    }

    private static boolean expired(AiTurnContext context, CardPlay play, long limit) {
        return context.now() > play.since() + limit;
    }

    private static Optional<AiDecision> fromMenu(AiTurnContext context, String key, CardPlay play) {
        Integer index = context.seat().getActionCards().get(play.alias());
        if (index == null) return stop(context, key, play, Stage.DONE);
        Optional<Match> button =
                ActionCardHand.playButton(context, index, play.since() - CardPrompts.CLOCK_SKEW_MILLIS);
        if (button.isEmpty()) {
            return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        PlayMemory.store(context, key, played(context, play.alias(), play.extra()));
        return Optional.of(button.get().press("play " + SabotageWindow.title(play.alias())));
    }

    private static Optional<AiDecision> afterPreset(AiTurnContext context, String key, CardPlay play) {
        if (context.seat().getActionCards().containsKey(play.alias())) return Optional.empty();
        if (censured(context.game(), context.seat())) return stop(context, key, play, Stage.DONE);
        boolean window = SabotageWindow.expected(context.game(), context.seat(), play.alias());
        CardPlay played =
                new CardPlay(window ? Stage.WINDOW : Stage.RESOLVE, context.now(), 0, play.alias(), "", window, 0, "");
        return move(context, key, played);
    }

    private static Optional<AiDecision> afterPlay(AiTurnContext context, String key, CardPlay play) {
        if (context.seat().getActionCards().containsKey(play.alias())) {
            return expired(context, play, CONFIRM_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        Optional<AiDecision> announcement =
                PlayMemory.takeAnnouncement(context, key).map(AiDecision.Announce::new);
        if (announcement.isPresent()) return announcement;
        return move(context, key, play.at(play.windowExpected() ? Stage.WINDOW : Stage.RESOLVE, context.now()));
    }

    private static Optional<AiDecision> duringWindow(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, play, Stage.CANCELED);
        }
        CardPlay current = play;
        if (current.announcementId().isBlank()) {
            Optional<AiPrompt> announcement = SabotageWindow.announcement(context, play.alias(), play.playedAt());
            if (announcement.isEmpty()) {
                return expired(context, play, ANNOUNCEMENT_MILLIS)
                        ? stop(context, key, play, Stage.DONE)
                        : Optional.empty();
            }
            current = current.withAnnouncement(announcement.get().messageId());
            PlayMemory.store(context, key, current);
        }
        rememberResolveButton(context, key, current);
        boolean overdue = context.now() > current.since() + WINDOW_CEILING_MILLIS;
        if (!overdue && SabotageWindow.open(context, current.announcementId(), current.since()))
            return Optional.empty();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, current, Stage.CANCELED);
        }
        return move(context, key, current.at(Stage.RESOLVE, context.now()));
    }

    private static Optional<AiDecision> resolve(AiTurnContext context, String key, CardPlay play) {
        Optional<AiCard> card = AiActionCards.of(play.alias());
        Game game = context.game();
        Player seat = context.seat();
        if (card.isEmpty() || censured(game, seat)) return stop(context, key, play, Stage.DONE);
        if (context.now() < play.since() + settleMillis(game, seat, play)) return Optional.empty();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, play, Stage.CANCELED);
        }
        if (!card.get().readyToResolve(context)) return Optional.empty();
        Stage after = card.get().hasTarget() ? Stage.TARGET : Stage.DONE;
        if (!card.get().hasResolveButton()) return move(context, key, play.at(after, context.now()));
        String handler = card.get().resolveHandler();
        Optional<Match> button = CardPrompts.ownedPublic(
                        context,
                        play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS,
                        id -> CardPrompts.matches(id, handler))
                .or(() -> rememberedResolveButton(context, key, handler));
        if (button.isEmpty()) {
            return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        context.memory().remove(REF_KEY + key);
        card.get().onResolve().accept(context);
        PlayMemory.moveTo(context, key, play, after);
        return Optional.of(button.get().press("resolve " + SabotageWindow.title(play.alias())));
    }

    private static void rememberResolveButton(AiTurnContext context, String key, CardPlay play) {
        String handler =
                AiActionCards.of(play.alias()).map(AiCard::resolveHandler).orElse(null);
        if (handler == null || context.memory().has(REF_KEY + key)) return;
        CardPrompts.ownedPublic(
                        context,
                        play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS,
                        id -> CardPrompts.matches(id, handler))
                .ifPresent(match -> context.memory()
                        .put(
                                REF_KEY + key,
                                String.join(
                                        FIELD,
                                        match.prompt().channelId(),
                                        match.prompt().messageId(),
                                        match.button().customId(),
                                        match.button().label())));
    }

    private static Optional<Match> rememberedResolveButton(AiTurnContext context, String key, String handler) {
        String[] fields = StringUtils.splitPreserveAllTokens(
                context.memory().get(REF_KEY + key).orElse(""), FIELD);
        if (fields == null || fields.length != 4) return Optional.empty();
        PromptButton button = new PromptButton(0, fields[2], handler, context.faction(), fields[3], false);
        AiPrompt prompt =
                new AiPrompt(fields[0], fields[1], AiPrompt.PromptSource.PUBLIC, "", List.of(button), context.now());
        if (context.alreadyPressed(prompt, button)) return Optional.empty();
        return Optional.of(new Match(prompt, button));
    }

    private static Optional<AiDecision> target(AiTurnContext context, String key, CardPlay play) {
        Optional<AiDecision> choice = AiActionCards.of(play.alias())
                .filter(AiCard::hasTarget)
                .flatMap(card -> card.target().next(context, key, play));
        if (choice.isPresent()) return choice;
        return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
    }
}
