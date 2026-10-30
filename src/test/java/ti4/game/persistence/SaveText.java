package ti4.game.persistence;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import ti4.game.Game;
import ti4.helpers.Constants;

/** The game's save file text, and a game read back from it, for tests that keep snapshots of a running game. */
public final class SaveText {

    private SaveText() {}

    // Writing a save stamps the game as modified now; the game's own stamp is put back, in the game and in the text, so
    // taking a snapshot changes nothing and the snapshot reads back as the game was.
    public static String of(Game game) {
        long lastModified = game.getLastModifiedDate();
        StringWriter writer = new StringWriter();
        try {
            GameSaveService.writeGame(game, writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            game.setLastModifiedDate(lastModified);
        }
        String stamp = Constants.LAST_MODIFIED_DATE + " ";
        return writer.toString()
                .lines()
                .map(line -> line.startsWith(stamp) ? stamp + lastModified : line)
                .collect(Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
    }

    public static Game read(String text) {
        List<String> lines = text.lines().toList();
        return GameLoadService.readGame(lines);
    }
}
