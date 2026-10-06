package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class MapSegmentTest extends BaseTi4Test {

    private static final Set<String> CORE = MapFrame.positionsWithin("000", 1);
    private static final Set<String> FAR_SOUTH = MapFrame.positionsWithin("1237", 1);

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("segment-test");
        game.setFowMode(true);
        CORE.forEach(position -> game.setTile(new Tile("19", position)));
        // 301 sits beyond an empty ring 2, so it is inside a radius-3 circle around 000 but not connected.
        game.setTile(new Tile("20", "301"));
    }

    private Set<String> positionsOf(String name) {
        return MapSegment.find(game, name).orElseThrow().positions();
    }

    private List<String> names() {
        return MapSegment.all(game).stream().map(MapSegment::name).toList();
    }

    @Test
    void segmentsAreSavedReplacedAndRemoved() {
        MapSegment.put(game, new MapSegment("home", "000", 3));
        MapSegment.put(game, MapSegment.cluster("open", "000", 0));
        MapSegment.put(game, MapSegment.cluster("capped", "000", 2));
        assertEquals("home=000:3;open=000:c;capped=000:c2", game.getStoredValue("fowMapSegments"));

        MapSegment.put(game, new MapSegment("home", "101", 4));
        assertEquals(new MapSegment("home", "101", 4), MapSegment.stored(game).getLast(), "same name replaces");

        assertTrue(MapSegment.remove(game, "home"));
        assertFalse(MapSegment.remove(game, "home"));
        assertEquals(
                List.of(MapSegment.cluster("open", "000", 0), MapSegment.cluster("capped", "000", 2)),
                MapSegment.stored(game));
    }

    @Test
    void brokenOrReservedStoredSegmentsAreSkipped() {
        game.setStoredValue(
                "fowMapSegments", "ok=000:2;bad=nowhere:2;big=000:12;Upper=000:1;main=000:1;fracture=000:1;junk");
        assertEquals(List.of(new MapSegment("ok", "000", 2)), MapSegment.stored(game));
    }

    @Test
    void namesAreShortLowercaseSlugsAndOnlyMainAndFractureAreReserved() {
        assertTrue(MapSegment.isValidName("north-2"));
        assertFalse(MapSegment.isValidName("North"));
        assertFalse(MapSegment.isValidName("with space"));
        assertFalse(MapSegment.isValidName("a".repeat(21)));
        assertTrue(MapSegment.isReservedName(MapSegment.MAIN));
        assertTrue(MapSegment.isReservedName(MapSegment.FRACTURE));
        assertFalse(MapSegment.isReservedName("orion"));
    }

    @Test
    void defaultSegmentIsClearedWhenItsSegmentIsRemoved() {
        MapSegment.put(game, new MapSegment("far", "1201", 2));
        MapSegment.setDefault(game, "far");
        assertEquals("far", MapSegment.defaultSegment(game).orElseThrow().name());

        MapSegment.remove(game, "far");
        MapSegment.put(game, new MapSegment("far", "1201", 2));
        assertTrue(MapSegment.defaultSegment(game).isEmpty(), "re-adding the name does not restore the default");
    }

    @Test
    void segmentIsVisibleOnlyWhenAKnownSystemLiesInside() {
        MapSegment.put(game, new MapSegment("home", "000", 1));
        MapSegment.put(game, new MapSegment("far", "1201", 1));

        assertEquals(List.of(new MapSegment("home", "000", 1)), MapSegment.visibleFrom(game, Set.of("101")));
        assertTrue(MapSegment.visibleFrom(game, Set.of("501")).isEmpty());
    }

    @Test
    void clusterStopsAtAnEmptyHexWhereACircleWouldNot() {
        MapSegment.put(game, new MapSegment("circle", "000", 3));
        MapSegment.put(game, MapSegment.cluster("core", "000", 0));

        assertTrue(positionsOf("circle").contains("301"));
        assertEquals(CORE, positionsOf("core"));
    }

    @Test
    void gapSettingAndRadiusLimitShapeACluster() {
        MapSegment.put(game, MapSegment.cluster("core", "000", 0));
        MapSegment.put(game, MapSegment.cluster("inner", "000", 1));
        MapSegment.setGap(game, 1);

        assertTrue(positionsOf("core").contains("301"), "gap 1 jumps the empty ring");
        assertEquals(CORE, positionsOf("inner"), "the radius still limits the cluster");
    }

    @Test
    void clusterGrowsWhenTheGmAddsTiles() {
        MapSegment.put(game, MapSegment.cluster("core", "000", 0));
        game.setTile(new Tile("21", "201"));
        assertTrue(positionsOf("core").containsAll(Set.of("201", "301")), "201 bridges the gap to 301");
    }

    @Test
    void everySeparateClusterBecomesAnAutomaticSectorWithAStableUniqueName() {
        FAR_SOUTH.forEach(position -> game.setTile(new Tile("22", position)));
        MapSegment.setAutoSectors(game, true);

        List<MapSegment> sectors = MapSegment.all(game);
        assertEquals(
                List.of(CORE, Set.of("301"), FAR_SOUTH),
                sectors.stream().map(MapSegment::positions).toList());
        assertEquals(names(), sectors.stream().map(MapSegment::name).toList(), "same names every call");
        assertEquals(3, Set.copyOf(names()).size(), "no two sectors share a name");
        assertTrue(SectorNames.NAMES.containsAll(names()));
    }

    @Test
    void namedSegmentsReplaceTheAutomaticSectorTheyCoverAndKeepTheirName() {
        FAR_SOUTH.forEach(position -> game.setTile(new Tile("22", position)));
        MapSegment.setAutoSectors(game, true);
        MapSegment.setGap(game, 1);
        String takenName = names().getLast();
        MapSegment.put(game, MapSegment.cluster(takenName, "000", 0));

        List<MapSegment> segments = MapSegment.all(game);
        assertEquals(2, segments.size());
        assertEquals(takenName, segments.getFirst().name(), "the GM keeps the name");
        assertEquals(FAR_SOUTH, segments.get(1).positions());
        assertNotEquals(takenName, segments.get(1).name(), "the automatic sector moved to another name");
    }

    private String sectorAt(String position) {
        return MapSegment.all(game).stream()
                .filter(segment -> segment.positions().contains(position))
                .findFirst()
                .orElseThrow()
                .name();
    }

    @Test
    void renamingAStoredSegmentKeepsItsShapeAndMovesTheDefault() {
        MapSegment.put(game, MapSegment.cluster("core", "000", 2));
        MapSegment.setDefault(game, "core");

        assertNull(MapSegment.rename(game, "core", "home"));
        assertEquals(List.of(MapSegment.cluster("home", "000", 2)), MapSegment.stored(game));
        assertEquals("home", MapSegment.defaultSegment(game).orElseThrow().name());
    }

    @Test
    void renamedAutomaticSectorKeepsItsNameAsItGrows() {
        MapSegment.setAutoSectors(game, true);
        assertNull(MapSegment.rename(game, sectorAt("000"), "home"));
        assertEquals("home", sectorAt("000"));

        // 201 joins the core to 301; the bigger renamed core keeps its name over the unnamed 301 sector.
        game.setTile(new Tile("21", "201"));
        assertEquals(List.of("home"), names());
        assertTrue(MapSegment.dormantNames(game).isEmpty());
    }

    @Test
    void mergedSectorsShowTheBiggerNameAndTheOtherReturnsOnASplit() {
        MapSegment.setAutoSectors(game, true);
        MapSegment.rename(game, sectorAt("000"), "home");
        MapSegment.rename(game, sectorAt("301"), "outpost");

        game.setTile(new Tile("21", "201"));
        assertEquals(List.of("home"), names());
        assertEquals(List.of(new MapSegment.Dormant("outpost", "home")), MapSegment.dormantNames(game));

        game.removeTile("201");
        assertEquals("home", sectorAt("000"));
        assertEquals("outpost", sectorAt("301"));
        assertTrue(MapSegment.dormantNames(game).isEmpty());
    }

    @Test
    void renamingAMergedSectorToAHiddenNameAbsorbsIt() {
        MapSegment.setAutoSectors(game, true);
        MapSegment.rename(game, sectorAt("000"), "home");
        MapSegment.rename(game, sectorAt("301"), "outpost");
        game.setTile(new Tile("21", "201"));

        assertNull(MapSegment.rename(game, "home", "outpost"));
        assertEquals(List.of("outpost"), names());
        assertTrue(MapSegment.dormantNames(game).isEmpty());
        assertEquals(1, game.getStoredValue("fowMapSectorNames").split(";").length);
    }

    @Test
    void removingARenamedSectorGivesItAnAutomaticNameAgain() {
        MapSegment.setAutoSectors(game, true);
        MapSegment.rename(game, sectorAt("000"), "home");

        assertTrue(MapSegment.remove(game, "home"));
        assertTrue(SectorNames.NAMES.contains(sectorAt("000")));
    }

    @Test
    void renameRejectsInvalidReservedAndTakenNames() {
        MapSegment.setAutoSectors(game, true);
        String core = sectorAt("000");
        String outpost = sectorAt("301");

        assertNotNull(MapSegment.rename(game, core, "Bad Name"));
        assertNotNull(MapSegment.rename(game, core, MapSegment.MAIN));
        assertNotNull(MapSegment.rename(game, core, outpost));
        assertNotNull(MapSegment.rename(game, "nothing-here", "home"));
        assertEquals(core, sectorAt("000"));
    }

    @Test
    void fractureIsABuiltInSegmentOnlyWhenTheOptionIsOn() {
        game.setTile(new Tile("25", "frac1"));
        assertFalse(MapSegment.isFractureSeparate(game));

        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);
        MapSegment fracture = MapSegment.find(game, MapSegment.FRACTURE).orElseThrow();
        assertTrue(fracture.isFracture());
        assertEquals(Set.of("frac1"), fracture.positions());
        assertTrue(MapSegment.stored(game).isEmpty(), "the built-in segment is never stored");
    }

    @Test
    void customAdjacencyJoinsSectorsButAWormholeLinkDoesNot() {
        MapSegment.setAutoSectors(game, true);
        // Tile 25 (Quann) has a beta wormhole: 101 and 1237 are wormhole-adjacent but stay separate sectors.
        game.setTile(new Tile("25", "101"));
        game.setTile(new Tile("25", "1237"));
        assertEquals(3, MapSegment.all(game).size(), "core, 301 and 1237");

        // A GM custom adjacency does connect them.
        game.addCustomAdjacentTiles("301", List.of("1237"));
        List<MapSegment> sectors = MapSegment.all(game);
        assertEquals(2, sectors.size());
        assertTrue(sectors.stream().anyMatch(sector -> sector.positions().containsAll(Set.of("301", "1237"))));
    }

    @Test
    void automaticSectorsKeepTheFractureAsItsOwnSector() {
        game.setTile(new Tile("25", "frac1"));
        game.setTile(new Tile("26", "frac2"));
        MapSegment.setAutoSectors(game, true);

        // Fracture tiles never cluster with the galaxy, so with automatic sectors they must still get a sector,
        // with or without the Separate Fracture option.
        assertEquals(Set.of("frac1", "frac2"), positionsOf(MapSegment.FRACTURE));
        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);
        assertEquals(Set.of("frac1", "frac2"), positionsOf(MapSegment.FRACTURE));
        assertEquals(1, names().stream().filter(MapSegment.FRACTURE::equals).count());
    }
}
