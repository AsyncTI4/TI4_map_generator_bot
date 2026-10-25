package ti4.image.map;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.testUtils.BaseTi4Test;

/**
 * Wall-clock timing of whole map renders (constructor = canvas sizing, draw = paint plus encode) for DisplayType.all.
 * Uses MapRenderer, so it only runs on checkouts that have the render pipeline split out; before the rewrite the same
 * measurement went through the old MapGenerator class. Production randomness and no mocks, as in the bot. The corpus
 * is rendered twice and the second pass is reported, so the JIT is warm.
 *
 * <pre>
 * mvn -o test -Dtest=MapRenderTimingBench -Dgolden.timing.saves=DIR -Dgolden.timing.out=FILE [-Dgolden.only=a,b]
 *     -DargLine=-Xmx10g
 * </pre>
 */
@EnabledIfSystemProperty(named = "golden.timing.saves", matches = ".+")
class MapRenderTimingBench extends BaseTi4Test {

    @Test
    void timeCorpus() throws Exception {
        Path savesDir = Path.of(System.getProperty("golden.timing.saves"));
        Path out = Path.of(System.getProperty("golden.timing.out"));
        String only = System.getProperty("golden.only", "");
        List<Path> saves;
        try (Stream<Path> files = Files.list(savesDir)) {
            saves = files.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> only.isBlank()
                            || List.of(only.split(","))
                                    .contains(path.getFileName().toString().replace(".txt", "")))
                    .sorted()
                    .toList();
        }
        List<String> lines = new ArrayList<>();
        for (int pass = 1; pass <= 2; pass++) {
            lines.clear();
            for (Path save : saves) {
                Game game = load(save);
                long start = System.nanoTime();
                RenderContext ctx = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.all, null, null));
                long constructed = System.nanoTime();
                MapRenderer.render(ctx, RenderTimings.disabled());
                long drawn = System.nanoTime();
                lines.add(String.format(
                        "%s\t%d\t%d\t%d\t%d",
                        save.getFileName().toString().replace(".txt", ""),
                        ctx.canvas().width(),
                        ctx.canvas().allocatedHeight(),
                        (constructed - start) / 1_000_000,
                        (drawn - constructed) / 1_000_000));
                System.out.println("[timing] pass " + pass + " " + lines.getLast());
            }
        }
        Files.writeString(
                out, "game\twidth\theight\tconstructorMs\tdrawAndEncodeMs\n" + String.join("\n", lines) + "\n");
    }

    private static Game load(Path save) throws Exception {
        Class<?> loader = Class.forName("ti4.game.persistence.GameLoadService");
        Method readGame = loader.getDeclaredMethod("readGame", File.class);
        readGame.setAccessible(true);
        return (Game) readGame.invoke(null, save.toFile());
    }
}
