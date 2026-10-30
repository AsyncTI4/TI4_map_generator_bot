package ti4.ai.eval;

import java.util.Collections;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;

@UtilityClass
public class Adjacency {

    private static final String MOVEMENT_MEMO = "adjacency.movement";
    private static final String WITH_SYSTEM_MEMO = "adjacency.withSystem";

    public static Set<String> forMovement(Game game, Player player, String position) {
        return DecisionMemo.remember(
                () -> Collections.unmodifiableSet(
                        FoWHelper.getAdjacentTiles(game, position, player, false, false, true)),
                MOVEMENT_MEMO,
                player,
                position);
    }

    public static Set<String> withSystem(Game game, Player player, String position) {
        return DecisionMemo.remember(
                () -> Collections.unmodifiableSet(FoWHelper.getAdjacentTiles(game, position, player, false, true)),
                WITH_SYSTEM_MEMO,
                player,
                position);
    }
}
