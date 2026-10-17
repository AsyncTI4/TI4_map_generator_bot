package ti4.ai.actioncards;

import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@FunctionalInterface
interface ActivationTest {

    boolean holds(Game game, Player seat, Tile activated);
}
