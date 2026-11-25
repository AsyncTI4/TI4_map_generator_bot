package ti4.ai.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import ti4.game.Game;
import ti4.game.persistence.SaveText;
import ti4.helpers.Constants;

/**
 * Reads a game recorded with -Dai.selfplay.savesdir (see GameRecorder) and, while the same seeded game plays again,
 * checks each save against the recorded one. Lines holding wall-clock times are left out of the comparison, since only
 * the virtual clock is replayed.
 */
final class SavedGameReplay {

    static final String PROPERTY = "ai.selfplay.replaygame";
    static final String FLAG_PREFIX = "flag.";
    // Flags that only shape the batch or this harness, not the game itself.
    private static final Set<String> BATCH_FLAGS = Set.of(
            "ai.selfplay.games",
            "ai.selfplay.threads",
            "ai.selfplay.full",
            "ai.selfplay.stopround",
            GameRecorder.PROPERTY,
            PROPERTY,
            PROPERTY + ".to",
            PROPERTY + ".continue");
    // Lines whose value comes from the real clock: the creation date, and the turn time the bot adds up from the real
    // clock against turn starts the harness stamps on the virtual one.
    private static final Set<String> WALL_CLOCK_KEYS = Set.of(Constants.CREATION_DATE, Constants.TOTAL_TURN_TIME);
    // Epoch milliseconds from the real clock (stamps such as last_modified_date, creation_date_time or the stored round
    // start times). The virtual clock starts at 1_790_000_000_000 and a game runs for at most a few virtual weeks, so
    // it
    // stays below this.
    private static final long WALL_CLOCK_FLOOR = 1_792_000_000_000L;
    private static final java.util.regex.Pattern EPOCH_MILLIS =
            java.util.regex.Pattern.compile("(?<!\\d)1\\d{12}(?!\\d)");

    private final Path folder;
    private final TreeMap<Integer, Path> saves;
    private final int stopAt;
    private int index;
    private int cachedIndex = -1;
    private String cachedText;
    private Divergence divergence;

    record Divergence(int save, String file, int line, String expected, String actual) {}

    SavedGameReplay(Path folder, int stopAt) {
        this.folder = folder;
        this.saves = saveFiles(folder);
        this.stopAt = stopAt;
    }

    static boolean replayFlag(String key) {
        return key.startsWith("ai.") && !BATCH_FLAGS.contains(key);
    }

    static Properties manifest(Path folder) {
        Properties manifest = new Properties();
        try (Reader reader = Files.newBufferedReader(folder.resolve(GameRecorder.MANIFEST), StandardCharsets.UTF_8)) {
            manifest.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return manifest;
    }

    // The game-shaping flags the recording ran with that this JVM does not have, or has with another value. Several
    // self-play classes read their flags once when they load, so a replay has to start with the recorded ones.
    static List<String> mismatchedFlags(Properties manifest) {
        List<String> mismatched = new ArrayList<>();
        for (String key : manifest.stringPropertyNames()) {
            if (!key.startsWith(FLAG_PREFIX)) continue;
            String flag = key.substring(FLAG_PREFIX.length());
            if (!replayFlag(flag)) continue;
            String recorded = manifest.getProperty(key);
            if (!recorded.equals(System.getProperty(flag))) mismatched.add("-D" + flag + "=" + recorded);
        }
        for (String flag : System.getProperties().stringPropertyNames()) {
            if (replayFlag(flag) && !manifest.containsKey(FLAG_PREFIX + flag)) mismatched.add("no -D" + flag);
        }
        return mismatched;
    }

    // The number of saves the recorded game made, or no limit when the recording did not finish.
    static int recordedSaves(Path folder) {
        Path finished = folder.resolve(GameRecorder.FINISHED);
        if (!Files.exists(finished)) return Integer.MAX_VALUE;
        Properties counts = new Properties();
        try (Reader reader = Files.newBufferedReader(finished, StandardCharsets.UTF_8)) {
            counts.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Integer.parseInt(counts.getProperty("saves"));
    }

    static TreeMap<Integer, Path> saveFiles(Path folder) {
        TreeMap<Integer, Path> files = new TreeMap<>();
        try (Stream<Path> listing = Files.list(folder.resolve(GameRecorder.SAVES))) {
            listing.filter(path -> path.getFileName().toString().endsWith(GameRecorder.SAVE_SUFFIX))
                    .forEach(path -> files.put(number(path), path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    static TreeMap<Integer, Path> aiStateFiles(Path folder) {
        TreeMap<Integer, Path> files = new TreeMap<>();
        try (Stream<Path> listing = Files.list(folder.resolve(GameRecorder.SAVES))) {
            listing.filter(path -> path.getFileName().toString().endsWith(GameRecorder.AI_SUFFIX))
                    .forEach(path -> files.put(number(path), path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    static int number(Path path) {
        return Integer.parseInt(path.getFileName().toString().substring(0, 6));
    }

    static String read(Path save) {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(save))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    synchronized void saved(Game game) {
        index++;
        if (done()) return;
        Map.Entry<Integer, Path> recorded = saves.floorEntry(index);
        if (recorded == null) {
            divergence = new Divergence(index, "-", 0, "(no recorded save)", "(a save)");
            return;
        }
        if (recorded.getKey() != cachedIndex) {
            cachedIndex = recorded.getKey();
            cachedText = read(recorded.getValue());
        }
        String replayed = SaveText.of(game);
        firstDifference(cachedText, replayed).ifPresent(line -> {
            divergence = new Divergence(
                    index, recorded.getValue().getFileName().toString(), line.number(), line.expected(), line.actual());
            keepReplayed(replayed);
        });
    }

    synchronized boolean done() {
        return divergence != null || index >= stopAt;
    }

    synchronized int savesChecked() {
        return divergence == null ? Math.min(index, stopAt) : divergence.save() - 1;
    }

    synchronized Optional<Divergence> divergence() {
        return Optional.ofNullable(divergence);
    }

    int recordedFiles() {
        return saves.size();
    }

    record Line(int number, String expected, String actual) {}

    private static final int CONTEXT = 160;

    // Long save lines (stored values) are cut to the stretch around the first character that differs.
    private static Line around(int number, String expected, String actual) {
        int at = 0;
        while (at < Math.min(expected.length(), actual.length()) && expected.charAt(at) == actual.charAt(at)) at++;
        int from = Math.max(0, at - CONTEXT);
        String prefix = from > 0 ? "..." : "";
        return new Line(
                number,
                prefix + expected.substring(from, Math.min(expected.length(), at + CONTEXT)),
                prefix + actual.substring(from, Math.min(actual.length(), at + CONTEXT)));
    }

    // The replayed save that first differed sits next to the recorded ones as replayed-NNNNNN.txt, to diff in full.
    private void keepReplayed(String replayed) {
        Path file = folder.resolve(String.format(java.util.Locale.ROOT, "replayed-%06d.txt", index));
        try {
            Files.writeString(file, replayed, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Optional<Line> firstDifference(String expected, String actual) {
        List<String> want = comparable(expected);
        List<String> got = comparable(actual);
        for (int i = 0; i < Math.max(want.size(), got.size()); i++) {
            String a = i < want.size() ? want.get(i) : "(end of save)";
            String b = i < got.size() ? got.get(i) : "(end of save)";
            if (!a.equals(b)) return Optional.of(around(i + 1, a, b));
        }
        return Optional.empty();
    }

    static String comparableText(String save) {
        return String.join("\n", comparable(save));
    }

    private static List<String> comparable(String save) {
        return save.lines()
                .filter(line -> WALL_CLOCK_KEYS.stream().noneMatch(key -> line.startsWith(key + " ")))
                .map(SavedGameReplay::maskWallClock)
                .toList();
    }

    private static String maskWallClock(String line) {
        return EPOCH_MILLIS
                .matcher(line)
                .replaceAll(
                        found -> Long.parseLong(found.group()) >= WALL_CLOCK_FLOOR ? "<wall clock>" : found.group());
    }
}
