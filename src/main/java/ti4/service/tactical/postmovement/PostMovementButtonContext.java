package ti4.service.tactical.postmovement;

import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;

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
