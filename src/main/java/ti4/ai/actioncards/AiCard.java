package ti4.ai.actioncards;

import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import ti4.game.Game;
import ti4.game.Player;

record AiCard(
        String key,
        Predicate<String> matches,
        Window window,
        ToDoubleBiFunction<Game, Player> playValue,
        ToDoubleBiFunction<Game, Player> availability,
        String resolveHandler,
        TargetStep target) {

    enum Window {
        COMPONENT_ACTION,
        PRE_PLAY,
        REACTION
    }

    boolean hasTarget() {
        return target != null;
    }

    boolean isSabotage() {
        return window == Window.REACTION && AiActionCards.SABOTAGE.equals(key);
    }

    double playValueFor(Game game, Player seat) {
        return playValue.applyAsDouble(game, seat);
    }

    double availabilityFor(Game game, Player seat) {
        return availability.applyAsDouble(game, seat);
    }
}
