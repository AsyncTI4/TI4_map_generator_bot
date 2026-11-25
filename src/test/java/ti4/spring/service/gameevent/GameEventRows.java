package ti4.spring.service.gameevent;

import ti4.game.Game;

/** The player state column the game events table stores, for test harnesses that write the same rows to a file. */
public final class GameEventRows {

    private GameEventRows() {}

    public static String playerState(Game game) {
        return CompactPlayerState.snapshot(game).full();
    }
}
