package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.Mapper;
import ti4.service.megagame.MegagamePlayerReach;
import ti4.testUtils.BaseTi4Test;

class MapFocusRenderTest extends BaseTi4Test {

    private static final String VIEWER_TILE = "301";
    private static final String FAR_TILE = "000";

    private Game game;
    private Player viewer;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-focus-render-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        viewer = seat("111", "sol", "blue");
        Player rival = seat("222", "hacan", "yellow");
        game.getTileByPosition(VIEWER_TILE)
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, viewer.getColorID()), 1);
        game.getTileByPosition(FAR_TILE)
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, rival.getColorID()), 1);
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    @Test
    void distanceGrowsTheLitAreaStepByStepFromThePlayersPresence() {
        Set<String> presence = MegagamePlayerReach.presence(game, viewer);

        assertEquals(Set.of(VIEWER_TILE), presence);
        assertEquals(presence, MegagamePlayerReach.withinDistance(game, viewer, presence, 0));
        Set<String> oneStep = MegagamePlayerReach.withinDistance(game, viewer, presence, 1);
        Set<String> expected =
                new HashSet<>(Adjacency.of(game, viewer).forVision().from(VIEWER_TILE));
        expected.add(VIEWER_TILE);
        assertEquals(expected, oneStep);
        assertFalse(oneStep.contains(FAR_TILE));
        assertTrue(MegagamePlayerReach.withinDistance(game, viewer, presence, 3).contains(FAR_TILE));
    }

    @Test
    void focusedRenderDarkensOnlyUnlitSystemsAndNeverGoesToTheWebsite() {
        MapFocus focus = new MapFocus("sol", Set.of(VIEWER_TILE));
        RenderContext plain = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.map, null, null));
        RenderContext focused = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.all, null, null, focus));
        RenderContext focusedMap = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.map, null, null, focus));
        BufferedImage plainImage = MapRenderer.paint(plain, RenderTimings.disabled());
        BufferedImage focusedImage = MapRenderer.paint(focusedMap, RenderTimings.disabled());

        assertSame(viewer, focused.focusPlayer());
        assertFalse(MapRenderer.render(focused, RenderTimings.disabled()).eligibleForWebsite());
        assertEquals(brightness(plainImage, plain, VIEWER_TILE), brightness(focusedImage, focusedMap, VIEWER_TILE));
        assertTrue(brightness(focusedImage, focusedMap, FAR_TILE) < brightness(plainImage, plain, FAR_TILE));
    }

    // Sums the inner part of the hex, away from the edges a dimmed neighbour's polygon could touch.
    private static long brightness(BufferedImage image, RenderContext ctx, String position) {
        Rectangle hex = ctx.board().hexBounds(position);
        long sum = 0;
        for (int x = hex.x + hex.width / 3; x < hex.x + 2 * hex.width / 3; x++) {
            for (int y = hex.y + hex.height / 3; y < hex.y + 2 * hex.height / 3; y++) {
                Color color = new Color(image.getRGB(x, y));
                sum += color.getRed() + color.getGreen() + color.getBlue();
            }
        }
        return sum;
    }
}
