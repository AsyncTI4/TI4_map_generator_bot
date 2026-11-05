package ti4.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.map.MapSegment;
import ti4.model.GalaxyNames;
import ti4.model.SegmentNames;
import ti4.service.map.MapJsonIOService.MapDataIO;
import ti4.service.map.MapJsonIOService.SectorIO;
import ti4.service.map.MapJsonIOService.SectorListIO;
import ti4.service.map.MapJsonIOService.TileIO;
import ti4.testUtils.BaseTi4Test;

/**
 * Sector and galaxy data in the map JSON. importSectors runs after every tile is placed, so these tests place
 * the tiles on the game first and then hand importSectors a MapDataIO describing the same positions - exactly
 * the state importMapFromJson is in when it reaches the sector pass.
 */
class MapSectorJsonServiceTest extends BaseTi4Test {

    private Game game;
    private MapDataIO data;
    private StringBuilder errors;

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);
        game = new Game();
        game.setName("sector-json-test");
        game.setFowMode(true);
        data = new MapDataIO();
        data.setMapInfo(new ArrayList<>());
        errors = new StringBuilder();
    }

    private TileIO place(String position) {
        game.setTile(new Tile("19", position));
        TileIO tile = new TileIO();
        tile.setPosition(position);
        tile.setTileID("19");
        data.getMapInfo().add(tile);
        return tile;
    }

    private static SectorIO sector(String type, Integer distance, String name) {
        SectorIO sector = new SectorIO();
        sector.setType(type);
        sector.setDistance(distance);
        sector.setName(name);
        return sector;
    }

    private SectorListIO listed(String name, String type, String centre, Integer distance, String... positions) {
        SectorListIO sector = new SectorListIO();
        sector.setName(name);
        sector.setType(type);
        sector.setCentre(centre);
        sector.setDistance(distance);
        sector.setPositions(List.of(positions));
        if (data.getSectors() == null) {
            data.setSectors(new ArrayList<>());
        }
        data.getSectors().add(sector);
        return sector;
    }

    private MapSegment segmentNamed(String name) {
        return MapSegment.find(game, name).orElseThrow(() -> new AssertionError("no segment " + name + ": " + errors));
    }

    @Test
    void manualCircleAndClusterBecomeStoredSegmentsCentredOnTheirSystem() {
        place("000").setSector(sector("manual_circle", 1, "core"));
        place("101");
        place("401").setSector(sector("manual_auto", 0, "rim"));
        place("402");

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(segmentNamed("core").kind()).isEqualTo(MapSegment.Kind.CIRCLE);
        assertThat(segmentNamed("core").centre()).isEqualTo("000");
        assertThat(segmentNamed("core").radius()).isEqualTo(1);
        assertThat(segmentNamed("rim").kind()).isEqualTo(MapSegment.Kind.CLUSTER);
        assertThat(segmentNamed("rim").positions()).containsExactlyInAnyOrder("401", "402");
        assertThat(errors).isEmpty();
    }

    @Test
    void sectorFieldsAreReadFromTheJsonText() {
        game.setFowMode(true);
        String json = """
                {
                  "sectorGap": 1,
                  "galaxies": { "main": "andromeda" },
                  "mapInfo": [
                    { "position": "000", "tileID": "19",
                      "sector": { "type": "manual_circle", "distance": 1, "name": "core" } },
                    { "position": "101", "tileID": "19" }
                  ]
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);

        assertThat(segmentNamed("core").radius()).isEqualTo(1);
        assertThat(MapSegment.gap(game)).isEqualTo(1);
        assertThat(GalaxyNames.name(game, "main")).isEqualTo("andromeda");
    }

    @Test
    void aliasesAreAcceptedForTheSectorType() {
        place("000").setSector(sector("Circle", 0, "core"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(segmentNamed("core").kind()).isEqualTo(MapSegment.Kind.CIRCLE);
    }

    // The automatic name sits on 402, not on the cluster's anchor (401): any member system may carry it.
    @Test
    void automaticNameOnAnyMemberSystemNamesItsCluster() {
        place("000").setSector(sector("automatic", 0, ""));
        place("101");
        place("401");
        place("402").setSector(sector("automatic", 0, "far-reach"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.isAutoSectors(game)).isTrue();
        MapSegment farReach = segmentNamed("far-reach");
        assertThat(farReach.kind()).isEqualTo(MapSegment.Kind.AUTO);
        assertThat(farReach.positions()).containsExactlyInAnyOrder("401", "402");
    }

    @Test
    void automaticSystemsThatDisagreeOnTheGapUseTheLargestAndWarn() {
        place("000").setSector(sector("automatic", 1, ""));
        place("401").setSector(sector("automatic", 2, ""));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.gap(game)).isEqualTo(2);
        assertThat(errors.toString()).contains("disagree");
    }

    // Scenario 3: without a gap a cluster stops at the first empty ring; sectorGap lets it jump one.
    @Test
    void sectorGapWidensAManualCluster() {
        place("000").setSector(sector("manual_auto", 0, "core"));
        place("201");
        data.setSectorGap(1);

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.isAutoSectors(game)).isFalse();
        assertThat(segmentNamed("core").positions()).contains("000", "201");
    }

    @Test
    void emptyManualNameGetsAWordFromTheSectorList() {
        place("000").setSector(sector("manual_circle", 0, null));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.stored(game)).hasSize(1);
        assertThat(SegmentNames.isValid(MapSegment.stored(game).getFirst().name()))
                .isTrue();
        assertThat(errors).isEmpty();
    }

    @Test
    void duplicateAndInvalidNamesAreReportedAndTheRestStillImports() {
        place("000").setSector(sector("manual_circle", 0, "core"));
        place("401").setSector(sector("manual_circle", 0, "core"));
        place("404").setSector(sector("manual_circle", 0, "Not A Valid Name!"));
        place("407").setSector(sector("manual_circle", 0, "main"));
        place("410").setSector(sector("manual_circle", 0, "outer"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.stored(game).stream().map(MapSegment::name)).containsExactlyInAnyOrder("core", "outer");
        assertThat(errors.toString()).contains("401").contains("404").contains("407");
    }

    @Test
    void unknownSectorTypeIsReported() {
        place("000").setSector(sector("spiral", 0, "core"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.stored(game)).isEmpty();
        assertThat(errors.toString()).contains("spiral");
    }

    // Scenario 11: the fracture is its own built-in segment, never part of a sector.
    @Test
    void sectorOnAFracturePositionIsRejected() {
        place("frac1").setSector(sector("manual_circle", 0, "broken"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.stored(game)).isEmpty();
        assertThat(errors.toString()).contains("frac1");
    }

    // Scenario 6: an automatic cluster touching a manual sector is dropped whole, so its name has no sector.
    @Test
    void automaticNameInAClusterTouchingAManualSectorIsReportedAsUnused() {
        place("000").setSector(sector("manual_circle", 0, "core"));
        place("101").setSector(sector("automatic", 0, "edge"));
        place("102");

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.find(game, "edge")).isEmpty();
        assertThat(errors.toString()).contains("`edge` is not used");
    }

    @Test
    void overlappingManualSectorsAreReported() {
        place("000").setSector(sector("manual_circle", 1, "core"));
        place("101").setSector(sector("manual_circle", 1, "inner"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(errors.toString()).contains("`core` and `inner` share");
    }

    @Test
    void galaxyNamesApplyManuallyAndEmptyStaysAutomatic() {
        game.setFowMode(true);
        place("000");
        place("a000");
        Map<String, String> galaxies = new LinkedHashMap<>();
        galaxies.put("main", "andromeda");
        galaxies.put("a", "");
        data.setGalaxies(galaxies);

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(GalaxyNames.name(game, "main")).isEqualTo("andromeda");
        assertThat(GalaxyNames.isManual(game, "main")).isTrue();
        assertThat(GalaxyNames.isManual(game, "a")).isFalse();
        assertThat(errors).isEmpty();
    }

    // Automatic galaxy names left over from the previous map must not block a name the file assigns elsewhere.
    @Test
    void staleAutomaticGalaxyNamesDoNotBlockImportedNames() {
        game.setFowMode(true);
        place("000");
        place("a000");
        game.setStoredValue("fowGalaxyNames", "b=andromeda");
        data.setGalaxies(Map.of("a", "andromeda"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(GalaxyNames.name(game, "a")).isEqualTo("andromeda");
        assertThat(errors).isEmpty();
    }

    @Test
    void sectorsInANonFogGameAreStoredWithAWarning() {
        game.setFowMode(false);
        place("000").setSector(sector("manual_circle", 0, "core"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.find(game, "core")).isPresent();
        assertThat(errors.toString()).contains("only used in Fog of War");
    }

    // Scenario 12: board positions only exist in fog games, so their names are skipped elsewhere.
    @Test
    void galaxyNamesInANonFogGameAreSkippedWithAnError() {
        game.setFowMode(false);
        place("000");
        data.setGalaxies(Map.of("main", "andromeda"));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(GalaxyNames.isManual(game, "main")).isFalse();
        assertThat(errors.toString()).contains("Fog of War");
    }

    @Test
    void fileWithoutSectorDataLeavesExistingSectorsAlone() {
        place("000");
        MapSegment.put(game, new MapSegment("core", "000", 1));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.find(game, "core")).isPresent();
    }

    @Test
    void fileWithSectorDataReplacesExistingSectors() {
        place("000").setSector(sector("manual_circle", 0, "fresh"));
        MapSegment.put(game, new MapSegment("stale", "000", 1));

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.find(game, "stale")).isEmpty();
        assertThat(MapSegment.find(game, "fresh")).isPresent();
    }

    @Test
    void exportThenImportKeepsSectorsAndGalaxyNames() {
        game.setFowMode(true);
        place("000").setSector(sector("manual_circle", 1, "core"));
        place("101");
        place("401").setSector(sector("manual_auto", 2, "rim"));
        place("402");
        place("a000").setSector(sector("automatic", 1, "beyond"));
        place("a101");
        data.setGalaxies(Map.of("a", "andromeda"));
        MapSectorJsonService.importSectors(game, data, errors);
        assertThat(errors).isEmpty();

        StringBuilder reimportErrors = new StringBuilder();
        Game reimported = reimport(export(), reimportErrors);

        assertThat(reimportErrors).isEmpty();
        assertThat(describe(reimported)).isEqualTo(describe(game));
        assertThat(GalaxyNames.name(reimported, "a")).isEqualTo("andromeda");
        assertThat(MapSegment.gap(reimported)).isEqualTo(MapSegment.gap(game));
    }

    @Test
    void listOnlyImportCreatesACircleAClusterAndAShape() {
        place("000");
        place("101");
        place("401");
        place("402");
        place("201");
        listed("core", "manual_circle", "000", 1, "000", "101");
        listed("rim", "cluster", "401", 0, "401", "402");
        listed("wedge", null, null, null, "201", "202", "306");

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(errors).isEmpty();
        assertThat(segmentNamed("core").kind()).isEqualTo(MapSegment.Kind.CIRCLE);
        assertThat(segmentNamed("core").radius()).isEqualTo(1);
        assertThat(segmentNamed("rim").kind()).isEqualTo(MapSegment.Kind.CLUSTER);
        assertThat(segmentNamed("rim").positions()).containsExactlyInAnyOrder("401", "402");
        // A shape keeps empty positions (202, 306), as a circle does.
        assertThat(segmentNamed("wedge").kind()).isEqualTo(MapSegment.Kind.SHAPE);
        assertThat(segmentNamed("wedge").positions()).containsExactlyInAnyOrder("201", "202", "306");
    }

    // Files from the map builder carry a circle both in the list and on its centre system.
    @Test
    void listEntryWinsOverAPerTileEntryWithTheSameName() {
        place("000").setSector(sector("manual_circle", 0, "core"));
        place("101");
        listed("core", "manual_circle", "000", 1, "000", "101");

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.stored(game)).hasSize(1);
        assertThat(segmentNamed("core").radius()).isEqualTo(1);
        assertThat(errors).isEmpty();
    }

    // The per-tile form cannot carry either sector: 000 has no system, and both share that centre.
    @Test
    void circlesOnAnEmptySharedCentreSurviveExportAndImport() {
        place("101");
        MapSegment.put(game, new MapSegment("hollow", "000", 1));
        MapSegment.put(game, new MapSegment("dot", "000", 0));

        MapDataIO exported = export();
        StringBuilder reimportErrors = new StringBuilder();
        Game reimported = reimport(exported, reimportErrors);

        assertThat(exported.getSectors().stream().map(SectorListIO::getCentre)).containsOnly("000");
        assertThat(reimportErrors).isEmpty();
        assertThat(describe(reimported)).isEqualTo(describe(game));
    }

    @Test
    void automaticListEntryNamesItsClusterWhenNoSystemDoes() {
        place("401");
        place("402");
        listed("orion", "automatic", null, null, "401", "402");

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.isAutoSectors(game)).isTrue();
        assertThat(segmentNamed("orion").positions()).containsExactlyInAnyOrder("401", "402");
        assertThat(errors).isEmpty();
    }

    @Test
    void perTileAutomaticNameWinsOverTheList() {
        place("401").setSector(sector("automatic", 0, "far-reach"));
        place("402");
        listed("orion", "automatic", null, null, "401", "402");

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(segmentNamed("far-reach").positions()).containsExactlyInAnyOrder("401", "402");
        assertThat(MapSegment.find(game, "orion")).isEmpty();
    }

    // Unrenamed automatic names come from a hash of the game name, so only the list keeps them across games.
    @Test
    void unrenamedAutomaticSectorKeepsItsNameThroughExportAndImport() {
        place("401").setSector(sector("automatic", 0, null));
        place("402");
        MapSectorJsonService.importSectors(game, data, errors);

        StringBuilder reimportErrors = new StringBuilder();
        Game reimported = reimport(export(), reimportErrors);

        assertThat(reimportErrors).isEmpty();
        assertThat(describe(reimported)).isEqualTo(describe(game));
    }

    @Test
    void manualListEntryWithoutACentreBecomesAShape() {
        place("401");
        listed("rim", "manual_circle", null, 1, "401", "402");

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(segmentNamed("rim").kind()).isEqualTo(MapSegment.Kind.SHAPE);
        assertThat(segmentNamed("rim").positions()).containsExactlyInAnyOrder("401", "402");
        assertThat(errors.toString()).contains("imported as a fixed shape");
    }

    @Test
    void segmentCapCountsShapes() {
        for (int i = 1; i <= MapSegment.MAX_SEGMENTS; i++) {
            listed("shape-" + i, null, null, null, String.format("4%02d", i));
        }
        listed("one-too-many", "manual_circle", "000", 0);

        MapSectorJsonService.importSectors(game, data, errors);

        assertThat(MapSegment.stored(game)).hasSize(MapSegment.MAX_SEGMENTS);
        assertThat(MapSegment.find(game, "one-too-many")).isEmpty();
        assertThat(errors.toString()).contains("maximum of " + MapSegment.MAX_SEGMENTS);
    }

    private MapDataIO export() {
        MapDataIO exported = new MapDataIO();
        exported.setMapInfo(game.getTileMap().values().stream()
                .map(tile -> {
                    TileIO io = new TileIO();
                    io.setPosition(tile.getPosition());
                    io.setTileID(tile.getTileID());
                    return io;
                })
                .collect(Collectors.toCollection(ArrayList::new)));
        MapSectorJsonService.exportSectors(game, exported);
        return exported;
    }

    private Game reimport(MapDataIO exported, StringBuilder reimportErrors) {
        Game reimported = new Game();
        reimported.setName("sector-json-reimport");
        reimported.setFowMode(true);
        game.getTileMap().values().forEach(tile -> reimported.setTile(new Tile(tile.getTileID(), tile.getPosition())));
        MapSectorJsonService.importSectors(reimported, exported, reimportErrors);
        return reimported;
    }

    private static List<String> describe(Game game) {
        return MapSegment.all(game).stream()
                .map(segment -> segment.name() + "|" + segment.kind() + "|"
                        + segment.positions().stream().sorted().toList())
                .sorted()
                .toList();
    }
}
