package ti4.service.tactical;

import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

public final class PostMovementButtonContext {
    public final Game game;
    public final Player player;
    public final Tile tile;

    public PostMovementButtonContext(Game game, Player player, Tile tile) {
        this.game = game;
        this.player = player;
        this.tile = tile;
    }
}
