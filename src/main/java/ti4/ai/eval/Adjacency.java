package ti4.ai.eval;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.winnu.LazaxGateFolding;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;

@UtilityClass
public class Adjacency {

    private static final String MOVEMENT_MEMO = "adjacency.movement";
    private static final String WITH_SYSTEM_MEMO = "adjacency.withSystem";

    public static Set<String> forMovement(Game game, Player player, String position) {
        return DecisionMemo.remember(
                () -> Collections.unmodifiableSet(withGateFolding(
                        game,
                        player,
                        position,
                        FoWHelper.getAdjacentTiles(game, position, player, false, false, true))),
                MOVEMENT_MEMO,
                player,
                position);
    }

    private static Set<String> withGateFolding(Game game, Player player, String position, Set<String> adjacent) {
        Set<String> links = LazaxGateFolding.movementLinks(game, player, position);
        if (links.isEmpty()) return adjacent;
        Set<String> all = new HashSet<>(adjacent);
        all.addAll(links);
        return all;
    }

    public static Set<String> withSystem(Game game, Player player, String position) {
        return DecisionMemo.remember(
                () -> Collections.unmodifiableSet(FoWHelper.getAdjacentTiles(game, position, player, false, true)),
                WITH_SYSTEM_MEMO,
                player,
                position);
    }
}
