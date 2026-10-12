package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

record AiCard(
        String key,
        Predicate<String> matches,
        Window window,
        Evaluator evaluator,
        ToDoubleBiFunction<Game, Player> availability,
        String resolveHandler,
        List<String> targetPrefixes,
        TargetStep target,
        Consumer<AiTurnContext> onResolve) {

    AiCard(
            String key,
            Predicate<String> matches,
            Window window,
            Evaluator evaluator,
            ToDoubleBiFunction<Game, Player> availability,
            String resolveHandler,
            List<String> targetPrefixes,
            TargetStep target) {
        this(key, matches, window, evaluator, availability, resolveHandler, targetPrefixes, target, context -> {});
    }

    enum Window {
        ECONOMY,
        COMPONENT_ACTION,
        AFTER_ACTIVATION,
        PRE_PLAY,
        AFTER_ACTION,
        REACTION
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

    double playValueFor(Game game, Player seat) {
        return planFor(game, seat).map(Plan::value).orElse(0.0);
    }

    double availabilityFor(Game game, Player seat) {
        return availability.applyAsDouble(game, seat);
    }
}
