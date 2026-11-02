package ti4.testUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;

public final class GameStateSnapshot {

    private static final int MAX_LINE = 140;

    private GameStateSnapshot() {}

    public static List<String> saveText(Game game) {
        try {
            Path file = Files.createTempFile("game-state-snapshot", ".txt");
            try {
                Class<?> saver = Class.forName("ti4.game.persistence.GameSaveService");
                Method saveGame = saver.getDeclaredMethod("saveGame", Game.class, Path.class);
                saveGame.setAccessible(true);
                saveGame.invoke(null, game, file);
                return normalised(Files.readAllLines(file, StandardCharsets.UTF_8));
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException | ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<String> changes(List<String> before, List<String> after) {
        List<String> changes = new ArrayList<>();
        java.util.Set<String> was = new java.util.HashSet<>(before);
        java.util.Set<String> now = new java.util.HashSet<>(after);
        for (String line : after) {
            if (!was.contains(line)) changes.add("+ " + abbreviate(line));
        }
        for (String line : before) {
            if (!now.contains(line)) changes.add("- " + abbreviate(line));
        }
        return changes;
    }

    private static String abbreviate(String line) {
        return line.length() > MAX_LINE ? line.substring(0, MAX_LINE) : line;
    }

    public static String fogFlags(Game game) {
        StringBuilder flags = new StringBuilder();
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) continue;
            flags.append(player.getFaction()).append(player.isFogInitialized()).append(':');
            for (Tile tile : new TreeMap<>(game.getTileMap()).values()) {
                flags.append(tile.hasFog(player) ? '1' : '0');
            }
            flags.append(';');
        }
        return flags.toString();
    }

    private static List<String> normalised(List<String> lines) {
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith(Constants.LAST_MODIFIED_DATE + " ")) continue;
            kept.add(sortedFogOptions(line));
        }
        return kept;
    }

    private static String sortedFogOptions(String line) {
        String prefix = "fow_options ";
        if (!line.startsWith(prefix)) return line;
        List<String> entries =
                new ArrayList<>(List.of(line.substring(prefix.length()).split(";")));
        Collections.sort(entries);
        return prefix + String.join(";", entries);
    }
}
