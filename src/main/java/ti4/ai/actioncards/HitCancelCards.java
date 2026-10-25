package ti4.ai.actioncards;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class HitCancelCards {

    static final double SHIELDS_MIN_WIN = 0.4;
    static final double SHIELDS_MAX_WIN = 0.8;
    static final int SHIELDS_MIN_HITS = 2;
    private static final String HOLDER = "hits";
    private static final String SEPARATOR = "_";

    enum Canceller {
        SHIELDS_HOLDING(List.of("sh1", "sh2", "sh3", "sh4"), "autoAssignSpaceHits_", "cancelSpaceHits_", 2),
        MANEUVERING_JETS(
                List.of("mjets1", "mjets2", "mjets3", "mjets4"),
                "autoAssignSpaceCannonOffenceHits_",
                "cancelPdsOffenseHits_",
                1);

        final List<String> aliases;
        final String assignPrefix;
        final String cancelPrefix;
        final int cancels;

        Canceller(List<String> aliases, String assignPrefix, String cancelPrefix, int cancels) {
            this.aliases = aliases;
            this.assignPrefix = assignPrefix;
            this.cancelPrefix = cancelPrefix;
            this.cancels = cancels;
        }

        static Optional<Canceller> of(String alias) {
            return Arrays.stream(values())
                    .filter(canceller -> canceller.aliases.contains(alias))
                    .findFirst();
        }

        Optional<String> heldBy(Player seat) {
            return seat.getActionCards().keySet().stream()
                    .filter(aliases::contains)
                    .sorted()
                    .findFirst();
        }

        long copiesHeldBy(Player seat) {
            return seat.getActionCards().keySet().stream()
                    .filter(aliases::contains)
                    .count();
        }
    }

    record Assignment(Canceller canceller, String position, int hits) {}

    static List<AiCard> all() {
        return Arrays.stream(Canceller.values())
                .flatMap(canceller -> canceller.aliases.stream()
                        .map(alias -> AiActionCards.hitAssignment(
                                alias, List.of(canceller.cancelPrefix), HitCancelCards::cancelStep)))
                .toList();
    }

    static Optional<AiDecision> cancelHits(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!AiSettings.isCombatCardsEnabled() || game.isFowMode() || !ActionCardRules.mayPlayNow(game, seat)) {
            return Optional.empty();
        }
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            Optional<Assignment> pending = assignment(context, prompt);
            if (pending.isEmpty()) continue;
            String key = PlayMemory.combatKey(pending.get().position(), HOLDER + prompt.messageId(), 0);
            if (context.memory().has(key)) continue;
            Optional<String> alias =
                    pending.get().canceller().heldBy(seat).filter(held -> worthCancelling(context, pending.get()));
            if (alias.isEmpty()) continue;
            int cancels = Math.min(pending.get().hits(), pending.get().canceller().cancels);
            Plan plan = Plan.aimedAt(0, CardChains.target(prompt.messageId(), String.valueOf(cancels)), "");
            Optional<AiDecision> play = ActionCardRules.startPlay(context, key, alias.get(), plan);
            if (play.isPresent()) return play;
        }
        return Optional.empty();
    }

    static Optional<Assignment> assignment(AiTurnContext context, AiPrompt prompt) {
        for (Canceller canceller : Canceller.values()) {
            Optional<PromptButton> assign = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && button.handlerId().startsWith(canceller.assignPrefix)
                    && !context.alreadyPressed(prompt, button));
            if (assign.isEmpty()) continue;
            String[] parts = StringUtils.removeStart(assign.get().handlerId(), canceller.assignPrefix)
                    .split(SEPARATOR);
            if (parts.length != 2 || !StringUtils.isNumeric(parts[1])) continue;
            int hits = Integer.parseInt(parts[1]);
            if (hits > 0 && cancelButton(context, prompt, canceller, parts[0]).isPresent()) {
                return Optional.of(new Assignment(canceller, parts[0], hits));
            }
        }
        return Optional.empty();
    }

    static boolean worthCancelling(AiTurnContext context, Assignment assignment) {
        return switch (assignment.canceller()) {
            case SHIELDS_HOLDING -> assignment.hits() >= SHIELDS_MIN_HITS && closeFight(context, assignment.position());
            case MANEUVERING_JETS -> !savedForTheInvasion(context, assignment.position());
        };
    }

    private static boolean closeFight(AiTurnContext context, String position) {
        return CombatForecast.lossChance(context.game(), context.seat(), position, BoardView.SPACE).stream()
                .map(loss -> 1 - loss)
                .anyMatch(win -> win >= SHIELDS_MIN_WIN && win <= SHIELDS_MAX_WIN);
    }

    private static boolean savedForTheInvasion(AiTurnContext context, String position) {
        Player seat = context.seat();
        if (!context.isActivePlayer() || Canceller.MANEUVERING_JETS.copiesHeldBy(seat) > 1) return false;
        return TacticalRules.rememberedPlan(context)
                .filter(plan -> plan.target().equals(position))
                .filter(plan -> CombatForecast.landingFacesSpaceCannon(
                        context.game(), seat, position, plan.landings().keySet()))
                .isPresent();
    }

    private static Optional<PromptButton> cancelButton(
            AiTurnContext context, AiPrompt prompt, Canceller canceller, String position) {
        String prefix = canceller.cancelPrefix + position + SEPARATOR;
        return prompt.firstEnabled(button -> (button.isUnowned() || button.isOwnedBy(context.faction()))
                && button.handlerId().startsWith(prefix)
                && StringUtils.isNumeric(StringUtils.removeStart(button.handlerId(), prefix))
                && Integer.parseInt(StringUtils.removeStart(button.handlerId(), prefix)) > 0
                && !context.alreadyPressed(prompt, button));
    }

    private static Optional<AiDecision> cancelStep(AiTurnContext context, String key, CardPlay play) {
        Optional<Canceller> canceller = Canceller.of(play.alias());
        String[] planned = play.extra().split("[" + CardChains.PARTS + "]", -1);
        if (canceller.isEmpty() || planned.length != 2 || !StringUtils.isNumeric(planned[1])) {
            PlayMemory.moveTo(context, key, play, Stage.DONE);
            return Optional.empty();
        }
        String position = PlayMemory.combatPosition(key);
        Optional<Match> cancel = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden() && prompt.messageId().equals(planned[0]))
                .flatMap(prompt -> cancelButton(context, prompt, canceller.get(), position).stream()
                        .map(button -> new Match(prompt, button)))
                .findFirst();
        if (cancel.isEmpty()) return Optional.empty();
        int left = Integer.parseInt(planned[1]) - 1;
        CardPlay next = play.withExtra(CardChains.target(planned[0], String.valueOf(left)));
        PlayMemory.moveTo(context, key, next, left > 0 ? Stage.TARGET : Stage.DONE);
        return Optional.of(cancel.get().press("cancel a hit with " + SabotageWindow.title(play.alias())));
    }
}
