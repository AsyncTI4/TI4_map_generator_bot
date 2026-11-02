package ti4.game.persistence;

import ti4.game.Game;

public record LoadedGameFile(Game game, GameFileStamp stamp) {}
