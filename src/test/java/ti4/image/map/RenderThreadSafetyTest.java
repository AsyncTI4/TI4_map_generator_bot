package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.persistence.GameSnapshots;
import ti4.helpers.DisplayType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class RenderThreadSafetyTest extends BaseTi4Test {

    private static Game gameWithTiles(String name, String tileId) {
        Game game = new Game();
        game.newGameSetup();
        game.setName(name);
        MapFrame.positionsWithin("000", 1).forEach(position -> game.setTile(new Tile(tileId, position)));
        return game;
    }

    // The first header line prints the render time, so it differs between any two renders; compare below it.
    private static final int TIMESTAMP_LINE_HEIGHT = 50;

    private static int[] pixels(Game game) {
        BufferedImage canvas = MapRenderer.render(
                        new MapRenderRequest(game, DisplayType.map, null, null), RenderTimings.disabled())
                .canvas();
        int height = canvas.getHeight() - TIMESTAMP_LINE_HEIGHT;
        return canvas.getRGB(0, TIMESTAMP_LINE_HEIGHT, canvas.getWidth(), height, null, 0, canvas.getWidth());
    }

    // The pipeline renders a copy taken when the render is queued; the handler keeps changing and saving the live
    // game while the render runs, and none of that may reach the image.
    @Test
    void snapshotRendersTheQueueTimeGameWhileTheLiveGameChanges() {
        Game live = gameWithTiles("snapshot-live", "19");
        Game queued = GameSnapshots.copy(live);
        int[] expected = pixels(GameSnapshots.copy(live));

        live.setTile(new Tile("20", "000"));
        live.setStoredValue("changedAfterQueue", "yes");

        assertArrayEquals(expected, pixels(queued));
        assertEquals("19", queued.getTileByPosition("000").getTileID());
        assertEquals("", queued.getStoredValue("changedAfterQueue"));
    }

    @Test
    void snapshotKeepsShownDistancesWhichTheSaveFileDoesNotStore() {
        Game live = gameWithTiles("snapshot-distances", "19");
        live.setTileDistances(new java.util.HashMap<>(java.util.Map.of("000", 0, "101", 1)));

        assertEquals(live.getTileDistances(), GameSnapshots.copy(live).getTileDistances());
    }

    @Test
    void twoGamesRenderingAtOnceMatchTheirSequentialRenders() throws Exception {
        Game first = gameWithTiles("concurrent-first", "19");
        Game second = gameWithTiles("concurrent-second", "20");
        int[] firstAlone = pixels(first);
        int[] secondAlone = pixels(second);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<int[]>> firsts = new ArrayList<>();
            List<Future<int[]>> seconds = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                firsts.add(pool.submit(() -> pixels(GameSnapshots.copy(first))));
                seconds.add(pool.submit(() -> pixels(GameSnapshots.copy(second))));
            }
            for (Future<int[]> render : firsts) assertArrayEquals(firstAlone, render.get(2, TimeUnit.MINUTES));
            for (Future<int[]> render : seconds) assertArrayEquals(secondAlone, render.get(2, TimeUnit.MINUTES));
        } finally {
            pool.shutdownNow();
        }
    }

    // ReloadMapperObjects reruns Mapper.init() while renders keep reading the data maps; a render must never see a
    // cleared or half-filled map.
    @Test
    void mapperReloadWhileRenderingNeverExposesMissingData() throws Exception {
        Game game = gameWithTiles("mapper-reload", "19");
        int[] expected = pixels(game);
        AtomicBoolean reloading = new AtomicBoolean(true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> reloads = pool.submit(() -> {
                try {
                    for (int i = 0; i < 3; i++) Mapper.init();
                } finally {
                    reloading.set(false);
                }
            });
            Future<Integer> renders = pool.submit(() -> {
                int count = 0;
                while (reloading.get() || count == 0) {
                    assertNotNull(Mapper.getUnit("cruiser"));
                    assertNotNull(Mapper.getColor("red"));
                    assertArrayEquals(expected, pixels(game));
                    count++;
                }
                return count;
            });
            reloads.get(5, TimeUnit.MINUTES);
            renders.get(5, TimeUnit.MINUTES);
        } finally {
            pool.shutdownNow();
        }
    }
}
