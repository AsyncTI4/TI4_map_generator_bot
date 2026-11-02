package ti4.image.map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.cache.CacheManager;
import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.image.io.ImageEncoder;
import ti4.testUtils.BaseTi4Test;

/**
 * Where warm map renders spend their time, for the image-helper performance work. The corpus is rendered once to fill
 * the image caches, then rendered golden.hotspots.passes more times (default 2) with a JFR recording running, so the
 * profile shows steady-state cost instead of first-time file reads. Paint and encode are timed separately, both as wall
 * clock and as CPU time of the rendering thread (steadier when other processes load the machine), and the
 * image caches report hits, misses, load time and the decoded size of what they hold.
 *
 * <pre>
 * mvn -o test -Dtest=ImageHotspotsBench -Dgolden.hotspots.saves=DIR -Dgolden.hotspots.out=DIR [-Dgolden.only=a,b]
 *     [-Dgolden.hotspots.types=all,map,stats] "-DargLine=-Xmx4g -XX:FlightRecorderOptions=stackdepth=512"
 * </pre>
 *
 * Analyse the recording with "jfr print --json --events jdk.ExecutionSample,jdk.NativeMethodSample warm.jfr".
 */
@EnabledIfSystemProperty(named = "golden.hotspots.saves", matches = ".+")
class ImageHotspotsBench extends BaseTi4Test {

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    @Test
    void profileWarmRenders() throws Exception {
        Path savesDir = Path.of(System.getProperty("golden.hotspots.saves"));
        Path out = Path.of(System.getProperty("golden.hotspots.out"));
        Files.createDirectories(out);
        int passes = Integer.getInteger("golden.hotspots.passes", 2);
        List<DisplayType> types = Stream.of(
                        System.getProperty("golden.hotspots.types", "all").split(","))
                .map(DisplayType::valueOf)
                .toList();
        List<Path> saves = listSaves(savesDir, System.getProperty("golden.only", ""));

        renderAll(saves, types, new long[4]);
        Map<String, CacheStats> before = snapshotStats();
        long[] warm = new long[4];
        try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.start();
            for (int pass = 0; pass < passes; pass++) {
                renderAll(saves, types, warm);
            }
            recording.stop();
            recording.dump(out.resolve("warm.jfr"));
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                "warm passes=%d renders=%d paintMs=%d encodeMs=%d paintCpuMs=%d encodeCpuMs=%d",
                passes,
                saves.size() * types.size() * passes,
                warm[0] / 1_000_000,
                warm[1] / 1_000_000,
                warm[2] / 1_000_000,
                warm[3] / 1_000_000));
        for (Map.Entry<String, Cache<?, ?>> entry : CacheManager.getNamesToCaches()) {
            if (!entry.getKey().toLowerCase().contains("image")) continue;
            CacheStats delta = entry.getValue().stats().minus(before.get(entry.getKey()));
            lines.add(String.format(
                    "%s size=%d decodedMB=%.1f warm hits=%d misses=%d hitRate=%.4f loadMs=%d",
                    entry.getKey(),
                    entry.getValue().estimatedSize(),
                    decodedMegabytes(entry.getValue()),
                    delta.hitCount(),
                    delta.missCount(),
                    delta.hitRate(),
                    delta.totalLoadTime() / 1_000_000));
        }
        Files.write(out.resolve("summary.txt"), lines);
        lines.forEach(line -> System.out.println("[hotspots] " + line));
    }

    private static void renderAll(List<Path> saves, List<DisplayType> types, long[] nanos) throws Exception {
        for (Path save : saves) {
            for (DisplayType type : types) {
                Game game = load(save);
                long start = System.nanoTime();
                long startCpu = THREADS.getCurrentThreadCpuTime();
                RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, type, null, null));
                BufferedImage canvas = MapRenderer.paint(context, RenderTimings.disabled());
                long painted = System.nanoTime();
                long paintedCpu = THREADS.getCurrentThreadCpuTime();
                ImageEncoder.forDiscord(canvas);
                long encoded = System.nanoTime();
                long encodedCpu = THREADS.getCurrentThreadCpuTime();
                nanos[0] += painted - start;
                nanos[1] += encoded - painted;
                nanos[2] += paintedCpu - startCpu;
                nanos[3] += encodedCpu - paintedCpu;
            }
        }
    }

    private static Map<String, CacheStats> snapshotStats() {
        Map<String, CacheStats> stats = new java.util.HashMap<>();
        for (Map.Entry<String, Cache<?, ?>> entry : CacheManager.getNamesToCaches()) {
            stats.put(entry.getKey(), entry.getValue().stats());
        }
        return stats;
    }

    private static double decodedMegabytes(Cache<?, ?> cache) {
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        long bytes = 0;
        for (Object value : cache.asMap().values()) {
            if (value instanceof BufferedImage image && seen.put(image, Boolean.TRUE) == null) {
                bytes += (long) image.getWidth() * image.getHeight() * 4;
            }
        }
        return bytes / (1024.0 * 1024.0);
    }

    private static List<Path> listSaves(Path dir, String only) throws Exception {
        List<String> wanted = only.isBlank() ? List.of() : List.of(only.split(","));
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> wanted.isEmpty()
                            || wanted.contains(path.getFileName().toString().replace(".txt", "")))
                    .sorted()
                    .toList();
        }
    }

    private static Game load(Path save) throws Exception {
        Class<?> loader = Class.forName("ti4.game.persistence.GameLoadService");
        Method readGame = loader.getDeclaredMethod("readGame", File.class);
        readGame.setAccessible(true);
        return (Game) readGame.invoke(null, save.toFile());
    }
}
