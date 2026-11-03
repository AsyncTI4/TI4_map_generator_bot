package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.tile.TileGeometry;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class MapRenderFrameTest extends BaseTi4Test {

    // Height of the fixed strip under a map-only image.
    private static final int STRIP = 600;
    // The 600x600 tile image has this much padding around the hex.
    private static final int TILE_PADDING = 100;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-context-frame-test");
        // Centre, ring 1 and one lone system on the north edge of ring 4: the canvas is sized for 4 rings,
        // but the placed tiles only span the upper half of it.
        MapFrame.positionsWithin("000", 1).forEach(position -> game.setTile(new Tile("19", position)));
        game.setTile(new Tile("20", "401"));
    }

    // A full 7-hex cluster around 1201 (far north) and a lone system at 1237 (far south): ~7000px apart.
    private static Game twoFarApartClusters() {
        Game twoMaps = new Game();
        twoMaps.newGameSetup();
        twoMaps.setName("two-maps-test");
        twoMaps.setFowMode(true);
        MapFrame.positionsWithin("1201", 1).forEach(position -> twoMaps.setTile(new Tile("19", position)));
        twoMaps.setTile(new Tile("20", "1237"));
        return twoMaps;
    }

    private static RenderContext render(Game game, DisplayType type, String segment) {
        return MapRenderer.resolve(new MapRenderRequest(game, type, null, segment));
    }

    private static void paint(RenderContext context) {
        MapRenderer.paint(context, RenderTimings.disabled());
    }

    @Test
    void segmentTitleIsSwappedForTheEasterEggOnlyOnARollOfZero() {
        assertTrue(SegmentTitle.isEasterEggRoll(0));
        assertFalse(SegmentTitle.isEasterEggRoll(1));
        assertFalse(SegmentTitle.isEasterEggRoll(199));
    }

    @Test
    void nonFogMapKeepsTheClassicRingBasedCanvas() {
        RenderContext context = render(game, DisplayType.map, null);
        assertEquals(
                Math.max(1000, BoardLayout.getMapWidth(game)), context.canvas().width());
        assertEquals(BoardLayout.getMapHeight(game) + STRIP, context.canvas().allocatedHeight());
    }

    @Test
    void fogMapIsFramedToThePlacedTiles() {
        int classicHeight = render(game, DisplayType.map, null).canvas().allocatedHeight();

        game.setFowMode(true);
        RenderContext framed = render(game, DisplayType.map, null);
        assertTrue(framed.canvas().allocatedHeight() < classicHeight, "empty outer rings are dropped");
        assertTrue(framed.canvas().width() < Math.max(1000, BoardLayout.getMapWidth(game)), "narrower too");
        assertTrue(framed.canvas().width() >= 11 * 150, "the 10-point score track still fits");
        paint(framed);
    }

    @Test
    void classicMapLayoutOptionTurnsFramingAndSectorsOff() {
        game.setFowMode(true);
        game.setFowOption(FOWOption.CLASSIC_MAP_LAYOUT, true);
        MapSegment.put(game, new MapSegment("core", "000", 1));
        RenderContext context = render(game, DisplayType.map, "core");
        assertEquals(
                Math.max(1000, BoardLayout.getMapWidth(game)), context.canvas().width());
        assertEquals(BoardLayout.getMapHeight(game) + STRIP, context.canvas().allocatedHeight());
        assertNull(context.board().shownSegmentName(), "sectors are ignored");
    }

    @Test
    void combinedViewUsesTheClassicWidthMatchingTheFramedMapSize() {
        game.setFowMode(true);
        assertEquals(
                BoardLayout.getMapWidth(game, 3),
                render(game, DisplayType.all, null).canvas().width());

        // A small far-away sector gets the same normal width, not the width of a "12-ring" map.
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));
        RenderContext south = render(twoMaps, DisplayType.all, "south");
        assertEquals(BoardLayout.getMapWidth(twoMaps, 3), south.canvas().width());
        paint(south);
    }

    @Test
    void requestedSegmentIsShownAndOnlyItsTilesAreDrawn() {
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("north", "1201", 1));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));

        RenderContext context = render(twoMaps, DisplayType.map, "south");
        assertEquals("south", context.board().shownSegmentName());
        assertTrue(context.board().isInShownRegion("1237"));
        assertFalse(context.board().isInShownRegion("1201"), "the other sector is not drawn");
    }

    @Test
    void gmViewOpensOnTheDefaultSegmentElseTheFirst() {
        Game twoMaps = twoFarApartClusters();
        MapSegment.put(twoMaps, new MapSegment("north", "1201", 2));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));
        assertEquals("north", render(twoMaps, DisplayType.map, null).board().shownSegmentName());

        MapSegment.setDefault(twoMaps, "south");
        assertEquals("south", render(twoMaps, DisplayType.map, null).board().shownSegmentName());
    }

    @Test
    void mapsSpreadBeyondNineRingsAreCappedToOneSubMap() {
        RenderContext context = render(twoFarApartClusters(), DisplayType.map, null);
        assertTrue(context.canvas().allocatedHeight() <= MapFrame.MAX_HEIGHT + STRIP, "within the 9-ring cap");
        assertTrue(context.canvas().width() <= MapFrame.MAX_WIDTH);
        paint(context);
    }

    @Test
    void separateFractureIsItsOwnMapAndNeverMixesWithTheGalaxy() {
        game.setFowMode(true);
        for (int index = 1; index <= 7; index++) {
            game.setTile(new Tile("2" + index, "frac" + index));
        }
        game.setTile(new Tile("25", "tl"));
        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);

        RenderContext main = render(game, DisplayType.map, null);
        assertTrue(main.board().isInShownRegion("000"));
        assertFalse(main.board().isInShownRegion("frac1"));
        paint(main);
        RenderContext fracture = render(game, DisplayType.map, MapSegment.FRACTURE);
        assertEquals(MapSegment.FRACTURE, fracture.board().shownSegmentName());
        assertTrue(fracture.board().isInShownRegion("frac1"));
        assertFalse(fracture.board().isInShownRegion("000"));
        assertTrue(fracture.board().pinnedCorners().isEmpty(), "corner tiles stay out of the Fracture view");
        paint(fracture);
    }

    @Test
    void separateFractureIsShownWhenItIsTheOnlySectorInView() {
        // Nothing but Fracture tiles: before the fix no sector was chosen, the frame fell back to the classic
        // canvas and galaxy and Fracture were drawn together.
        Game fractureOnly = new Game();
        fractureOnly.newGameSetup();
        fractureOnly.setName("fracture-only-test");
        fractureOnly.setFowMode(true);
        for (int index = 1; index <= 7; index++) {
            fractureOnly.setTile(new Tile("2" + index, "frac" + index));
        }
        fractureOnly.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);

        RenderContext context = render(fractureOnly, DisplayType.map, null);
        assertEquals(MapSegment.FRACTURE, context.board().shownSegmentName());
        assertTrue(context.board().isInShownRegion("frac1"));
        assertFalse(context.board().isInShownRegion("000"));
        paint(context);
    }

    @Test
    void knownCornerTileIsPinnedInsideTheFrameWithItsOwnColumn() {
        game.setFowMode(true);
        int contentWidthWithoutCorner =
                render(game, DisplayType.map, null).board().frameContentWidth();

        game.setTile(new Tile("25", "tl"));
        RenderContext context = render(game, DisplayType.map, null);
        assertEquals(Set.of("tl"), context.board().pinnedCorners());
        assertTrue(context.board().frameContentWidth() > contentWidthWithoutCorner, "a column was added for it");

        Point hex = context.board().pinnedCornerTileOrigin("tl");
        hex.translate(TILE_PADDING, TILE_PADDING);
        assertTrue(hex.x >= 0
                && hex.x + TileGeometry.TILE_WIDTH <= context.canvas().width());
        assertTrue(hex.y >= 0
                && hex.y + TileGeometry.TILE_HEIGHT <= context.canvas().allocatedHeight());
        paint(context);
    }

    @Test
    void pinnedCornersAreDrawnInEverySectorView() {
        Game twoMaps = twoFarApartClusters();
        twoMaps.setTile(new Tile("25", "br"));
        MapSegment.put(twoMaps, new MapSegment("south", "1237", 1));
        RenderContext context = render(twoMaps, DisplayType.map, "south");
        assertTrue(context.board().isInShownRegion("br"));
    }

    @Test
    void askingForTheMainMapShowsItEvenWhenNamedSectorsExist() {
        // The core around 000 is a named sector; 401 sits outside every sector on the main map.
        game.setFowMode(true);
        MapSegment.put(game, new MapSegment("core", "000", 1));

        assertNull(
                render(game, DisplayType.map, MapSegment.MAIN).board().shownSegmentName(),
                "main is the main view, not a fallback sector");
        assertEquals(
                "core",
                render(game, DisplayType.map, null).board().shownSegmentName(),
                "no request still opens the default sector");
    }
}
