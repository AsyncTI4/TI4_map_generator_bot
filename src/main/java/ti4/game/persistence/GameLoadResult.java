package ti4.game.persistence;

import ti4.game.Game;

sealed interface GameLoadResult {

    record Loaded(Game game) implements GameLoadResult {}

    record Missing() implements GameLoadResult {}

    record Corrupt(long fileLastModified, Exception cause) implements GameLoadResult {}
}
