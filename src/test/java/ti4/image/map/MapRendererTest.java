package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.testUtils.BaseTi4Test;

class MapRendererTest extends BaseTi4Test {

    private static Game smallGame() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("map-renderer-test");
        MapFrame.positionsWithin("000", 1).forEach(position -> game.setTile(new Tile("19", position)));
        return game;
    }

    @Test
    void renderEncodesTheCanvasAsWebpAndKeepsTheCanvasSize() {
        MapRenderResult result = MapRenderer.render(
                new MapRenderRequest(smallGame(), DisplayType.map, null, null), RenderTimings.disabled());

        assertEquals("webp", result.encoded().format());
        assertEquals("RIFF", new String(Arrays.copyOf(result.encoded().bytes(), 4), StandardCharsets.US_ASCII));
        assertTrue(result.canvas().getWidth() > 0 && result.canvas().getHeight() > 0);
    }

    @Test
    void publicFullRenderIsEligibleForTheWebsiteButMapOnlyIsNot() {
        Game game = smallGame();

        assertTrue(MapRenderer.render(new MapRenderRequest(game, DisplayType.all, null, null), RenderTimings.disabled())
                .eligibleForWebsite());
        assertEquals(
                false,
                MapRenderer.render(new MapRenderRequest(game, DisplayType.map, null, null), RenderTimings.disabled())
                        .eligibleForWebsite());
    }

    @Test
    void shownDistancesSurvivePaintAndAreClearedWhenTheRenderCompletes() {
        Game game = smallGame();
        game.setTileDistances(new HashMap<>(Map.of("000", 1)));
        RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.map, null, null));

        MapRenderer.paint(context, RenderTimings.disabled());
        assertFalse(game.getTileDistances().isEmpty());

        MapRenderer.draw(context, RenderTimings.disabled());
        assertTrue(game.getTileDistances().isEmpty());
    }

    @Test
    void aStatsOnlyRenderKeepsTheDistancesForALaterMapRender() {
        Game game = smallGame();
        game.setTileDistances(new HashMap<>(Map.of("000", 1)));
        RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.stats, null, null));

        MapRenderer.draw(context, RenderTimings.disabled());

        assertFalse(game.getTileDistances().isEmpty());
    }
}
