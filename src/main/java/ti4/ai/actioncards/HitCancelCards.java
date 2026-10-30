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
    private static final List<String> SHIELDS_HOLDING_ALIASES = List.of("sh1", "sh2", "sh3", "sh4");
    private static final List<String> MANEUVERING_JETS_ALIASES = List.of("mjets1", "mjets2", "mjets3", "mjets4");
    private static final String HOLDER = "hits";
    private static final String SEPARATOR = "_";
    private static final String INTERLOCKING_SUFFIX = "_interlocking";
    private static final String PLAN_PARTS = "[" + CardChains.PARTS + "]";

    enum Canceller {
        SHIELDS_HOLDING(SHIELDS_HOLDING_ALIASES, "autoAssignSpaceHits_", "cancelSpaceHits_", 2),
        JETS_AGAINST_SHIPS(MANEUVERING_JETS_ALIASES, "autoAssignSpaceCannonOffenceHits_", "cancelPdsOffenseHits_", 1),
        JETS_AGAINST_GROUND_FORCES(MANEUVERING_JETS_ALIASES, "autoAssignGroundHits_", "cancelPdsDefenceHits_", 1);

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

    private record CancelOffer(PromptButton button, String position, int hits) {

        static Optional<CancelOffer> of(PromptButton button, String prefix) {
            String[] parts = StringUtils.removeStart(button.handlerId(), prefix).split(SEPARATOR);
            if (parts.length < 2 || !StringUtils.isNumeric(parts[1])) return Optional.empty();
            return Optional.of(new CancelOffer(button, parts[0], Integer.parseInt(parts[1])));
        }
    }

    static List<AiCard> all() {
        return Arrays.stream(Canceller.values())
                .flatMap(canceller -> canceller.aliases.stream())
                .distinct()
                .map(alias -> AiActionCards.hitAssignment(alias, cancelPrefixesOf(alias), HitCancelCards::cancelStep))
                .toList();
    }

    private static List<String> cancelPrefixesOf(String alias) {
        return Arrays.stream(Canceller.values())
                .filter(canceller -> canceller.aliases.contains(alias))
                .map(canceller -> canceller.cancelPrefix)
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
            String target = CardChains.target(
                    prompt.messageId(),
                    String.valueOf(cancels),
                    pending.get().canceller().name());
            Optional<AiDecision> play =
                    ActionCardRules.startPlay(context, key, alias.get(), Plan.aimedAt(0, target, ""));
            if (play.isPresent()) return play;
        }
        return Optional.empty();
    }

    static Optional<Assignment> assignment(AiTurnContext context, AiPrompt prompt) {
        for (Canceller canceller : Canceller.values()) {
            boolean assignable = prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(canceller.assignPrefix)
                            && !context.alreadyPressed(prompt, button))
                    .isPresent();
            if (!assignable) continue;
            Optional<CancelOffer> cancel = cancelOffer(context, prompt, canceller);
            if (cancel.isPresent()) {
                return Optional.of(new Assignment(
                        canceller, cancel.get().position(), cancel.get().hits()));
            }
        }
        return Optional.empty();
    }

    static boolean worthCancelling(AiTurnContext context, Assignment assignment) {
        return switch (assignment.canceller()) {
            case SHIELDS_HOLDING -> assignment.hits() >= SHIELDS_MIN_HITS && closeFight(context, assignment.position());
            case JETS_AGAINST_SHIPS -> !savedForTheInvasion(context, assignment.position());
            case JETS_AGAINST_GROUND_FORCES -> true;
        };
    }

    private static boolean closeFight(AiTurnContext context, String position) {
        return CombatForecast.lossChance(context.game(), context.seat(), position, BoardView.SPACE).stream()
                .map(loss -> 1 - loss)
                .anyMatch(win -> win >= SHIELDS_MIN_WIN && win <= SHIELDS_MAX_WIN);
    }

    private static boolean savedForTheInvasion(AiTurnContext context, String position) {
        Player seat = context.seat();
        if (!context.isActivePlayer() || Canceller.JETS_AGAINST_SHIPS.copiesHeldBy(seat) > 1) return false;
        return TacticalRules.rememberedPlan(context)
                .filter(plan -> plan.target().equals(position))
                .filter(plan -> CombatForecast.landingFacesSpaceCannon(
                        context.game(), seat, position, plan.landings().keySet()))
                .isPresent();
    }

    private static Optional<CancelOffer> cancelOffer(AiTurnContext context, AiPrompt prompt, Canceller canceller) {
        return prompt.enabledButtons().stream()
                .filter(button -> button.isUnowned() || button.isOwnedBy(context.faction()))
                .filter(button -> button.handlerId().startsWith(canceller.cancelPrefix)
                        && !button.handlerId().endsWith(INTERLOCKING_SUFFIX)
                        && !context.alreadyPressed(prompt, button))
                .map(button -> CancelOffer.of(button, canceller.cancelPrefix))
                .flatMap(Optional::stream)
                .filter(offer -> offer.hits() > 0)
                .findFirst();
    }

    private static Optional<AiDecision> cancelStep(AiTurnContext context, String key, CardPlay play) {
        String[] planned = play.extra().split(PLAN_PARTS, -1);
        Optional<Canceller> canceller = planned.length == 3
                ? Arrays.stream(Canceller.values())
                        .filter(candidate -> candidate.name().equals(planned[2]))
                        .findFirst()
                : Optional.empty();
        if (canceller.isEmpty() || !StringUtils.isNumeric(planned[1])) {
            PlayMemory.moveTo(context, key, play, Stage.DONE);
            return Optional.empty();
        }
        String position = PlayMemory.combatPosition(key);
        Optional<Match> cancel = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden() && prompt.messageId().equals(planned[0]))
                .flatMap(prompt -> cancelOffer(context, prompt, canceller.get())
                        .filter(offer -> offer.position().equals(position))
                        .map(offer -> new Match(prompt, offer.button()))
                        .stream())
                .findFirst();
        if (cancel.isEmpty()) return Optional.empty();
        int left = Integer.parseInt(planned[1]) - 1;
        CardPlay next = play.withExtra(CardChains.target(planned[0], String.valueOf(left), planned[2]));
        PlayMemory.moveTo(context, key, next, left > 0 ? Stage.TARGET : Stage.DONE);
        return Optional.of(cancel.get().press("cancel a hit with " + SabotageWindow.title(play.alias())));
    }
}
