package ti4.service.tactical;

import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

public record PostMovementButtonContext(Game game, Player player, Tile tile) {}
