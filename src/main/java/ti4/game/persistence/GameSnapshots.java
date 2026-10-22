package ti4.game.persistence;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import ti4.game.Game;

public final class GameSnapshots {

    private GameSnapshots() {}

    public static Game copy(Game game) {
        StringWriter writer = new StringWriter();
        try {
            GameSaveService.writeGame(game, writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Game snapshot = GameLoadService.readGame(writer.toString().lines().toList());
        copyTransientState(game, snapshot);
        return snapshot;
    }

    private static void copyTransientState(Game game, Game snapshot) {
        snapshot.setTileDistances(new HashMap<>(game.getTileDistances()));
    }
}
