package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.playerarea.NeighbourMap;
import ti4.image.playerarea.PlayerAreaContext;
import ti4.image.playerarea.PlayerAreas;
import ti4.image.tile.SystemContextImage;
import ti4.model.Mapper;
import ti4.service.fow.FogViewer;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.FogEvents;

class FogViewerRenderTest extends BaseTi4Test {

    private static final String FAR_FROM_VIEWER = "000";
    private static final String VIEWER_TILE = "301";

    private Game game;
    private Player viewer;
    private Player rival;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("fog-viewer-render-test");
        game.setFowMode(true);
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        viewer = seat("111", "sol", "blue");
        rival = seat("222", "hacan", "yellow");
        game.getTileByPosition(VIEWER_TILE)
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, viewer.getColorID()), 1);
        game.getTileByPosition(FAR_FROM_VIEWER)
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, rival.getColorID()), 2);
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    private BufferedImage systemImage(GenericInteractionCreateEvent event) {
        try (MockedStatic<DateTimeHelper> dateTime = mockStatic(DateTimeHelper.class, Mockito.CALLS_REAL_METHODS)) {
            dateTime.when(DateTimeHelper::getFormattedTimestampWithMillis).thenReturn("stamp");
            return SystemContextImage.render(game, event, 0, FAR_FROM_VIEWER, null);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test
    void systemImageFromAThreadUnderAPrivateChannelIsFoggedLikeThePrivateChannelItself() {
        BufferedImage fromThread = systemImage(FogEvents.eventInThreadUnder("111", "fog-111-private"));
        BufferedImage fromChannel = systemImage(FogEvents.eventIn("111", "fog-111-private"));
        BufferedImage unfogged = systemImage(FogEvents.eventIn("111", "game-actions"));

        assertArrayEquals(pixels(fromChannel), pixels(fromThread));
        assertFalse(java.util.Arrays.equals(pixels(unfogged), pixels(fromThread)));
    }

    @Test
    void systemImageForAPrivateViewWithoutAViewerDoesNotShowTheSystem() {
        BufferedImage fromGm = systemImage(FogEvents.eventIn("999", "gm-private"));
        BufferedImage unfogged = systemImage(FogEvents.eventIn("111", "game-actions"));

        assertFalse(java.util.Arrays.equals(pixels(unfogged), pixels(fromGm)));
    }

    @Test
    void fullMapForAPrivateViewWithoutAViewerRendersWithNoSystemsVisible() {
        GenericInteractionCreateEvent gm = FogEvents.eventIn("999", "gm-private");
        for (DisplayType type : new DisplayType[] {DisplayType.all, DisplayType.stats, DisplayType.map}) {
            RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, type, gm, null));

            assertTrue(context.fog().isPrivate());
            assertTrue(context.fog().visibleSystems().isEmpty());
            assertTrue(context.fog().tiles().isEmpty());
            BufferedImage[] canvas = new BufferedImage[1];
            assertDoesNotThrow(() -> canvas[0] = MapRenderer.paint(context, RenderTimings.disabled()));
            assertNotNull(canvas[0]);
        }
    }

    @Test
    void playerAreaForAPrivateViewWithoutAViewerDrawsWithoutThrowing() {
        BufferedImage image = new BufferedImage(3000, 450, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        FogViewer nobody = new FogViewer(game, true, null);
        PlayerAreaContext ctx =
                PlayerAreaContext.of(graphics, game, nobody, new WebsiteOverlays(), new NeighbourMap(game));

        assertDoesNotThrow(() -> PlayerAreas.drawSingle(ctx, rival, new Point(5, 5), 3000));
        graphics.dispose();
    }

    @Test
    void sideStatsForAPrivateViewWithoutAViewerPlaceNoStatTiles() {
        FogView fog = FogView.of(game, FogEvents.eventIn("999", "gm-private"));

        MapSideStatLayout.Layout layout = new MapSideStatLayout(game, fog).get();

        assertTrue(layout.playerStatTiles().isEmpty());
    }
}
