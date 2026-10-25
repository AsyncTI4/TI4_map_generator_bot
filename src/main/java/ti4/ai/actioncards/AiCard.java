package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

record AiCard(
        String key,
        Predicate<String> matches,
        Window window,
        Evaluator evaluator,
        KeepFloor keepFloor,
        String resolveHandler,
        List<String> targetPrefixes,
        TargetStep target,
        Consumer<AiTurnContext> onResolve,
        Function<Game, String> playSuffix) {

    AiCard(
            String key,
            Predicate<String> matches,
            Window window,
            Evaluator evaluator,
            KeepFloor keepFloor,
            String resolveHandler,
            List<String> targetPrefixes,
            TargetStep target,
            Consumer<AiTurnContext> onResolve) {
        this(key, matches, window, evaluator, keepFloor, resolveHandler, targetPrefixes, target, onResolve, game -> "");
    }

    AiCard(
            String key,
            Predicate<String> matches,
            Window window,
            Evaluator evaluator,
            KeepFloor keepFloor,
            String resolveHandler,
            List<String> targetPrefixes,
            TargetStep target) {
        this(key, matches, window, evaluator, keepFloor, resolveHandler, targetPrefixes, target, context -> {});
    }

    @FunctionalInterface
    interface KeepFloor {

        double of(Game game, Player seat, String alias);
    }

    enum Window {
        ECONOMY,
        COMPONENT_ACTION,
        AFTER_ACTIVATION,
        PRE_PLAY,
        AFTER_ACTION,
        REACTION,
        AGENDA_WHEN,
        AGENDA_AFTER,
        COMBAT_ROUND,
        HIT_ASSIGNMENT,
        PRODUCTION
    }

    boolean readyToResolve(AiTurnContext context) {
        return window != Window.AGENDA_WHEN || AgendaCards.revealSettled(context);
    }

    boolean hasTarget() {
        return target != null;
    }

    boolean hasResolveButton() {
        return resolveHandler != null;
    }

    boolean isSabotage() {
        return window == Window.REACTION && AiActionCards.SABOTAGE.equals(key);
    }

    boolean isComponentAction() {
        return window == Window.ECONOMY || window == Window.COMPONENT_ACTION;
    }

    Optional<Plan> planFor(Game game, Player seat) {
        return evaluator.plan(game, seat);
    }

    Optional<Plan> planFor(Game game, Player seat, PlayMoment moment) {
        return evaluator.plan(game, seat, moment);
    }

    double playValueFor(Game game, Player seat) {
        return planFor(game, seat).map(Plan::value).orElse(0.0);
    }

    double keepFloorFor(Game game, Player seat, String alias) {
        return keepFloor.of(game, seat, alias);
    }

    AiCard withKeepFloor(KeepFloor floor) {
        return new AiCard(
                key, matches, window, evaluator, floor, resolveHandler, targetPrefixes, target, onResolve, playSuffix);
    }
}
