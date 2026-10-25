package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.RenderRandom;
import ti4.image.io.EncodedImage;
import ti4.image.io.ImageEncoder;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class MapCanvasHeightTest extends BaseTi4Test {

    private static final int GENEROUS_TAIL = 80;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-canvas-height-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        List<String> factions = List.of("sol", "hacan", "jolnar", "letnev");
        List<String> colors = List.of("blue", "yellow", "purple", "red");
        for (int i = 0; i < factions.size(); i++) {
            Player player = game.addPlayer("p" + i, factions.get(i));
            player.setFaction(game, factions.get(i));
            player.setColor(colors.get(i));
            player.setUnitsOwned(
                    new HashSet<>(Mapper.getFaction(factions.get(i)).getUnits()));
        }
    }

    private BufferedImage paint(DisplayType type) {
        return paint(type, null);
    }

    private BufferedImage paint(DisplayType type, Integer allocatedHeight) {
        try (MockedStatic<DateTimeHelper> dateTime = mockStatic(DateTimeHelper.class, Mockito.CALLS_REAL_METHODS);
                AutoCloseable random = RenderRandom.neutral()) {
            dateTime.when(DateTimeHelper::getFormattedTimestamp).thenReturn("2000.01.01 - 00.00.00");
            RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, type, null, null));
            return allocatedHeight == null
                    ? MapRenderer.paint(context, RenderTimings.disabled())
                    : MapRenderer.paint(context, RenderTimings.disabled(), allocatedHeight);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int lastNonBlackRow(BufferedImage image) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    return y;
                }
            }
        }
        return -1;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private void addSpectators(int count) {
        for (int i = 0; i < count; i++) {
            game.addPlayer("spectator" + i, "a_spectator_with_a_rather_long_name_" + i);
        }
    }

    @Test
    void statsCanvasEndsJustBelowThePlayerAreas() {
        BufferedImage image = paint(DisplayType.stats);

        int tail = image.getHeight() - 1 - lastNonBlackRow(image);
        assertTrue(tail >= 0 && tail < GENEROUS_TAIL, "tail below the last drawn pixel was " + tail);
    }

    @Test
    void fullCanvasEndsJustBelowThePlayerAreas() {
        BufferedImage image = paint(DisplayType.all);

        int tail = image.getHeight() - 1 - lastNonBlackRow(image);
        assertTrue(tail >= 0 && tail < GENEROUS_TAIL, "tail below the last drawn pixel was " + tail);
    }

    @Test
    void wrappedSpectatorLinesAreFullyInsideTheImage() {
        int withoutSpectators = paint(DisplayType.stats).getHeight();
        addSpectators(60);

        BufferedImage image = paint(DisplayType.stats);

        assertTrue(image.getHeight() > withoutSpectators + 100, "several wrapped lines make the canvas taller");
        int tail = image.getHeight() - 1 - lastNonBlackRow(image);
        assertTrue(tail >= 0 && tail < GENEROUS_TAIL, "tail below the last spectator line was " + tail);
    }

    @Test
    void anEstimateThatIsTooSmallGrowsTheCanvasInsteadOfClippingThePlayerAreas() {
        BufferedImage normal = paint(DisplayType.stats);

        BufferedImage grown = paint(DisplayType.stats, 600);

        assertEquals(normal.getWidth(), grown.getWidth());
        assertEquals(normal.getHeight(), grown.getHeight());
        assertArrayEquals(pixels(normal), pixels(grown));
    }

    @Test
    void anEstimateThatCutsThroughThePlayerAreasRedrawsOnlyThem() {
        BufferedImage normal = paint(DisplayType.all);

        BufferedImage grown = paint(DisplayType.all, normal.getHeight() - 600);

        assertEquals(normal.getHeight(), grown.getHeight());
        assertArrayEquals(pixels(normal), pixels(grown));
    }

    @Test
    void anEstimateThatIsTooSmallEvenForTheInfoAreaRendersAgain() {
        BufferedImage normal = paint(DisplayType.all);

        BufferedImage grown = paint(DisplayType.all, normal.getHeight() / 2);

        assertEquals(normal.getHeight(), grown.getHeight());
        assertArrayEquals(pixels(normal), pixels(grown));
    }

    @Test
    void anOversizedEstimateIsCroppedToTheSameImage() {
        BufferedImage normal = paint(DisplayType.stats);

        BufferedImage cropped = paint(DisplayType.stats, normal.getHeight() * 2);

        assertEquals(normal.getHeight(), cropped.getHeight());
        assertArrayEquals(pixels(normal), pixels(cropped));
    }

    @Test
    void aCroppedCanvasEncodesAtItsCroppedSize() throws Exception {
        BufferedImage cropped =
                paint(DisplayType.stats, paint(DisplayType.stats).getHeight() * 2);

        EncodedImage encoded = ImageEncoder.forDiscord(cropped);

        BufferedImage decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(encoded.bytes()));
        assertEquals(cropped.getWidth(), decoded.getWidth());
        assertEquals(cropped.getHeight(), decoded.getHeight());
    }

    @Test
    void theEncoderPicksTheFormatFromTheCroppedSize() throws Exception {
        BufferedImage tall = new BufferedImage(40, 16_500, BufferedImage.TYPE_INT_RGB);

        assertEquals("jpg", ImageEncoder.forDiscord(tall).format());
        assertEquals(
                "webp",
                ImageEncoder.forDiscord(tall.getSubimage(0, 0, 40, 16_000)).format());
    }

    @Test
    void landscapeRendersAgainWhenTheEstimateIsTooSmall() {
        BufferedImage normal = paint(DisplayType.landscape);

        BufferedImage grown = paint(DisplayType.landscape, normal.getHeight() - 300);

        assertEquals(normal.getHeight(), grown.getHeight());
        assertArrayEquals(pixels(normal), pixels(grown));
    }

    @Test
    void drawnContentBelowTheGivenRowKeepsTheCanvasLongEnough() {
        BufferedImage image = new BufferedImage(20, 100, BufferedImage.TYPE_INT_RGB);
        image.setRGB(3, 70, 0x010101);

        assertEquals(71, CanvasBottom.ofDrawnContent(image, 40));
        assertEquals(90, CanvasBottom.ofDrawnContent(image, 90));
    }

    @Test
    void landscapePlayerAreasFillTheColumnRightOfTheMap() {
        BufferedImage image = paint(DisplayType.landscape);

        int framePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = image.getWidth() - 8; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    framePixels++;
                }
            }
        }
        assertTrue(framePixels > 500, "player area frames reach the right edge of the column, found " + framePixels);
    }

    @Test
    void landscapeSpectatorsWrapInsideTheColumn() {
        int withoutSpectators = paint(DisplayType.landscape).getHeight();
        addSpectators(400);

        BufferedImage image = paint(DisplayType.landscape);

        assertTrue(image.getHeight() > withoutSpectators, "the wrapped spectator lines extend the canvas");
    }

    private int heightForGameInfo(DisplayType type) {
        return MapRenderer.resolve(new MapRenderRequest(game, type, null, null))
                .canvas()
                .heightForGameInfo();
    }

    @Test
    void gameModeBadgesGetRoomBelowTheTimestampInStatsAndLandscapeRenders() {
        int statsWithoutBadges = heightForGameInfo(DisplayType.stats);
        int landscapeWithoutBadges = heightForGameInfo(DisplayType.landscape);

        game.setAbsolMode(true);

        assertEquals(statsWithoutBadges + GameModeBadges.ROOM_ABOVE_HEADER, heightForGameInfo(DisplayType.stats));
        assertEquals(
                landscapeWithoutBadges + GameModeBadges.ROOM_ABOVE_HEADER, heightForGameInfo(DisplayType.landscape));
    }

    @Test
    void gameModeBadgesDoNotMoveTheHeaderOfRendersThatDrawTheMapAboveIt() {
        int withoutBadges = heightForGameInfo(DisplayType.all);

        game.setAbsolMode(true);

        assertEquals(withoutBadges, heightForGameInfo(DisplayType.all));
    }
}
