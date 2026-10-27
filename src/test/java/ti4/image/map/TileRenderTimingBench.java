package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.tile.SystemContextImage;
import ti4.testUtils.BaseTi4Test;

/**
 * Wall-clock timing of the tile renderer alone: the board tiles of a DisplayType.all map render (BoardSection plus the
 * connection strip, drawn onto a canvas of the render's size) and the standalone system context images (one ring
 * around Mecatol and every home system). Production randomness and no mocks. The corpus is timed twice and the second
 * pass is reported, so the JIT is warm.
 *
 * <pre>
 * mvn -o test -Dtest=TileRenderTimingBench -Dgolden.timing.saves=DIR -Dgolden.timing.out=FILE [-Dgolden.only=a,b]
 *     -DargLine=-Xmx10g
 * </pre>
 */
@EnabledIfSystemProperty(named = "golden.timing.saves", matches = ".+")
class TileRenderTimingBench extends BaseTi4Test {

    private static final int CONTEXT_RINGS = 1;

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
            long boardTotal = 0;
            long contextTotal = 0;
            for (Path save : saves) {
                long board = timeBoard(load(save));
                Game game = load(save);
                Set<String> focus = focusPositions(game);
                long contextStart = System.nanoTime();
                for (String position : focus) {
                    contextImage(game, position);
                }
                long context = System.nanoTime() - contextStart;
                boardTotal += board;
                contextTotal += context;
                lines.add(String.format(
                        "%s\t%d\t%d\t%d",
                        save.getFileName().toString().replace(".txt", ""),
                        board / 1_000_000,
                        focus.size(),
                        context / 1_000_000));
                System.out.println("[tile-timing] pass " + pass + " " + lines.getLast());
            }
            lines.add(String.format("TOTAL\t%d\t-\t%d", boardTotal / 1_000_000, contextTotal / 1_000_000));
            System.out.println("[tile-timing] pass " + pass + " " + lines.getLast());
        }
        Files.writeString(out, "game\tboardTilesMs\tcontextImages\tcontextMs\n" + String.join("\n", lines) + "\n");
    }

    private static long timeBoard(Game game) {
        RenderContext ctx = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.all, null, null));
        BufferedImage canvas =
                new BufferedImage(ctx.canvas().width(), ctx.canvas().allocatedHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            long start = System.nanoTime();
            BoardSection.draw(ctx, g2);
            ConnectionStripSection.draw(ctx, g2);
            return System.nanoTime() - start;
        } finally {
            g2.dispose();
        }
    }

    private static Set<String> focusPositions(Game game) {
        Set<String> positions = new LinkedHashSet<>();
        Tile mecatol = game.getMecatolTile();
        if (mecatol != null) positions.add(mecatol.getPosition());
        for (Player player : game.getRealPlayers()) {
            Tile home = player.getHomeSystemTile();
            if (home != null) positions.add(home.getPosition());
        }
        return positions;
    }

    private static void contextImage(Game game, String position) {
        SystemContextImage.render(game, null, CONTEXT_RINGS, position, null);
    }

    private static Game load(Path save) throws Exception {
        Class<?> loader = Class.forName("ti4.game.persistence.GameLoadService");
        Method readGame = loader.getDeclaredMethod("readGame", File.class);
        readGame.setAccessible(true);
        return (Game) readGame.invoke(null, save.toFile());
    }
}
