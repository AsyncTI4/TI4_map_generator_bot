package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class MapSegmentClusterTest extends BaseTi4Test {

    private static final Set<String> CORE = MapFrame.positionsWithin("000", 1);
    private static final Set<String> FAR_SOUTH = MapFrame.positionsWithin("1237", 1);

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("segment-cluster-test");
        game.setFowMode(true);
        CORE.forEach(position -> game.setTile(new Tile("19", position)));
        // 301 sits beyond an empty ring 2, so it is inside a radius-3 circle around 000 but not connected.
        game.setTile(new Tile("20", "301"));
    }

    private Set<String> positionsOf(String name) {
        return MapSegment.find(game, name).orElseThrow().positions();
    }

    @Test
    void clusterStopsAtAnEmptyHexWhereACircleWouldNot() {
        MapSegment.put(game, new MapSegment("circle", "000", 3));
        MapSegment.put(game, MapSegment.cluster("core", "000", 0));

        assertTrue(positionsOf("circle").contains("301"));
        assertEquals(CORE, positionsOf("core"));
    }

    @Test
    void gapSettingLetsAClusterJumpOneEmptyHex() {
        MapSegment.put(game, MapSegment.cluster("core", "000", 0));
        MapSegment.setGap(game, 1);

        assertTrue(positionsOf("core").contains("301"));
        assertEquals(1, MapSegment.gap(game));
    }

    @Test
    void radiusLimitsAClusterToTheCircle() {
        MapSegment.setGap(game, 1);
        MapSegment.put(game, MapSegment.cluster("inner", "000", 1));
        assertEquals(CORE, positionsOf("inner"));
    }

    @Test
    void clusterGrowsWhenTheGmAddsTiles() {
        MapSegment.put(game, MapSegment.cluster("core", "000", 0));
        game.setTile(new Tile("21", "201"));
        assertTrue(positionsOf("core").containsAll(Set.of("201", "301")), "201 bridges the gap to 301");
    }

    @Test
    void everySeparateClusterBecomesAnAutomaticSector() {
        FAR_SOUTH.forEach(position -> game.setTile(new Tile("22", position)));
        MapSegment.setAutoSectors(game, true);

        List<MapSegment> sectors = MapSegment.all(game);
        assertEquals(3, sectors.size());
        assertEquals(CORE, sectors.getFirst().positions());
        assertEquals(Set.of("301"), sectors.get(1).positions());
        assertEquals(FAR_SOUTH, sectors.get(2).positions());
    }

    @Test
    void automaticSectorsGetStableUniqueWordNames() {
        FAR_SOUTH.forEach(position -> game.setTile(new Tile("22", position)));
        MapSegment.setAutoSectors(game, true);

        List<String> names = MapSegment.all(game).stream().map(MapSegment::name).toList();
        assertEquals(names, MapSegment.all(game).stream().map(MapSegment::name).toList(), "same names every call");
        assertEquals(names.size(), Set.copyOf(names).size(), "no two sectors share a name");
        for (String name : names) {
            assertTrue(MapSegment.isValidName(name), name + " must be a valid segment name");
            assertTrue(SectorNames.NAMES.contains(name), name + " comes from the word list");
        }
    }

    @Test
    void automaticNamesAvoidNamesTheGmAlreadyUses() {
        MapSegment.setAutoSectors(game, true);
        String autoName = MapSegment.all(game).getFirst().name();
        MapSegment.put(game, new MapSegment(autoName, "1237", 0));

        List<String> names = MapSegment.all(game).stream().map(MapSegment::name).toList();
        assertEquals(names.size(), Set.copyOf(names).size(), "the automatic sector moved to another word");
    }

    @Test
    void namedSegmentsReplaceTheAutomaticSectorTheyCover() {
        FAR_SOUTH.forEach(position -> game.setTile(new Tile("22", position)));
        MapSegment.setAutoSectors(game, true);
        MapSegment.setGap(game, 1);
        MapSegment.put(game, MapSegment.cluster("home", "000", 0));

        List<MapSegment> segments = MapSegment.all(game);
        assertEquals(2, segments.size());
        assertEquals("home", segments.getFirst().name());
        assertEquals(FAR_SOUTH, segments.get(1).positions());
    }

    @Test
    void clusterSegmentsSurviveSavingAndLoading() {
        MapSegment.put(game, MapSegment.cluster("open", "000", 0));
        MapSegment.put(game, MapSegment.cluster("capped", "000", 2));

        List<MapSegment> stored = MapSegment.stored(game);
        assertEquals(List.of(MapSegment.cluster("open", "000", 0), MapSegment.cluster("capped", "000", 2)), stored);
        assertEquals("open=000:c;capped=000:c2", game.getStoredValue("fowMapSegments"));
    }

    @Test
    void onlyMainAndFractureAreReserved() {
        assertTrue(MapSegment.isReservedName(MapSegment.MAIN));
        assertTrue(MapSegment.isReservedName(MapSegment.FRACTURE));
        assertFalse(MapSegment.isReservedName("andromeda"));
    }

    @Test
    void automaticSectorsAreOffByDefault() {
        assertFalse(MapSegment.isAutoSectors(game));
        assertTrue(MapSegment.all(game).isEmpty(), "no sectors without the setting or GM segments");
    }
}
