package ti4.game.persistence;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import ti4.game.Game;

/** The game's save file text, and a game read back from it, for tests that keep snapshots of a running game. */
public final class SaveText {

    private SaveText() {}

    public static String of(Game game) {
        StringWriter writer = new StringWriter();
        try {
            GameSaveService.writeSnapshot(game, writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    public static Game read(String text) {
        List<String> lines = text.lines().toList();
        return GameLoadService.readGame(lines);
    }
}
