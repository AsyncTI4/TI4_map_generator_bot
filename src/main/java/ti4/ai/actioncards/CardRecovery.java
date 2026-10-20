package ti4.ai.actioncards;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.agenda.AgendaClock;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;

@UtilityClass
class CardRecovery {

    private static final long CONFIRM_MILLIS = 60_000L;

    static void recover(AiTurnContext context) {
        String phase = context.game().getPhaseOfGame();
        if (phase.toLowerCase().startsWith("agenda")) {
            recoverAgenda(context);
            return;
        }
        if (!"action".equalsIgnoreCase(phase)) return;
        recoverCombat(context);
        if (context.isActivePlayer()) {
            recoverOwnTurn(context);
        } else {
            recoverReaction(context);
        }
    }

    private static void recoverAgenda(AiTurnContext context) {
        String key = PlayMemory.agendaKey(context);
        if (context.memory().has(key)) return;
        long since = AgendaClock.seenAt(context) - CardPrompts.CLOCK_SKEW_MILLIS;
        for (AiCard card : AiActionCards.inWindow(Window.AGENDA_WHEN)) {
            if (resolvePending(context, key, card, since)) return;
        }
    }

    private static void recoverOwnTurn(AiTurnContext context) {
        String key = PlayMemory.turnKey(context);
        if (context.memory().has(key)) return;
        for (AiCard card : AiActionCards.componentCards()) {
            if (resolvePending(context, key, card) || targetPending(context, key, card)) return;
        }
    }

    private static void recoverReaction(AiTurnContext context) {
        if (context.game().getActiveSystem().isBlank()) return;
        String key = PlayMemory.reactionKey(context);
        if (context.memory().has(key)) return;
        for (AiCard card : AiActionCards.inWindow(Window.AFTER_ACTIVATION)) {
            if (resolvePending(context, key, card)) return;
        }
    }

    private static boolean resolvePending(AiTurnContext context, String key, AiCard card) {
        return resolvePending(context, key, card, Prompts.turnStart(context));
    }

    private static boolean resolvePending(AiTurnContext context, String key, AiCard card, long since) {
        return resolvePending(context, key, card, card.key(), since);
    }

    private static boolean resolvePending(AiTurnContext context, String key, AiCard card, String alias, long since) {
        if (!card.hasResolveButton()) return false;
        String handler = card.resolveHandler();
        Optional<Match> resolve = CardPrompts.ownedPublic(context, since, id -> CardPrompts.matches(id, handler));
        if (resolve.isEmpty()) return false;
        long playedAt = resolve.get().prompt().createdAtMillis() - CONFIRM_MILLIS;
        Optional<AiPrompt> announcement = SabotageWindow.announcement(context, alias, playedAt);
        CardPlay rebuilt = new CardPlay(
                Stage.WINDOW,
                context.now(),
                playedAt,
                alias,
                announcement.map(AiPrompt::messageId).orElse(""),
                announcement.isPresent(),
                SabotageWindow.lastPlayIndex(context.game(), context.seat(), alias),
                "");
        PlayMemory.store(context, key, announcement.isPresent() ? rebuilt : rebuilt.at(Stage.RESOLVE, context.now()));
        return true;
    }

    private static void recoverCombat(AiTurnContext context) {
        String position = context.game().getActiveSystem();
        if (position.isBlank() || CombatCardRules.tracksAPlay(context)) return;
        String key = PlayMemory.combatKey(position, CombatCardRules.RECOVERED, 0);
        long since = Prompts.turnStart(context);
        for (AiCard card : AiActionCards.inWindow(Window.COMBAT_ROUND)) {
            if (playedUncanceled(context, card) && resolvePending(context, key, card, since)) return;
        }
    }

    private static boolean targetPending(AiTurnContext context, String key, AiCard card) {
        if (!card.hasTarget() || !playedUncanceled(context, card)) return false;
        long since = Prompts.turnStart(context);
        for (String prefix : card.targetPrefixes()) {
            Optional<AiPrompt> prompt = Prompts.newestFirst(context.prompts()).stream()
                    .filter(candidate -> !candidate.isHidden() && candidate.createdAtMillis() >= since)
                    .filter(candidate -> candidate.buttons().stream()
                            .anyMatch(button -> CardPrompts.matches(button.handlerId(), prefix)))
                    .filter(candidate -> offeredToSeat(context, candidate))
                    .findFirst();
            if (prompt.isEmpty()) continue;
            long playedAt = prompt.get().createdAtMillis() - CardPrompts.CLOCK_SKEW_MILLIS;
            PlayMemory.store(
                    context,
                    key,
                    new CardPlay(
                            Stage.TARGET,
                            context.now(),
                            playedAt,
                            card.key(),
                            "",
                            false,
                            SabotageWindow.lastPlayIndex(context.game(), context.seat(), card.key()),
                            ""));
            return true;
        }
        return false;
    }

    private static boolean playedUncanceled(AiTurnContext context, AiCard card) {
        if (context.seat().getActionCards().containsKey(card.key())) return false;
        return SabotageWindow.latestPlayBy(context.game(), context.seat(), card.key())
                .filter(play -> !play.isCanceled())
                .isPresent();
    }

    private static boolean offeredToSeat(AiTurnContext context, AiPrompt prompt) {
        boolean owned = prompt.buttons().stream().anyMatch(button -> button.isOwnedBy(context.faction()));
        return owned || Prompts.mentions(prompt, context.seat()) || namesNobody(context, prompt);
    }

    private static boolean namesNobody(AiTurnContext context, AiPrompt prompt) {
        return context.game().getRealPlayers().stream().noneMatch(player -> Prompts.mentions(prompt, player));
    }
}
