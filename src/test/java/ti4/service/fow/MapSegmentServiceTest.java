package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.MapSegment;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

class MapSegmentServiceTest extends BaseTi4Test {

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("segment-service-test");
        game.setFowMode(true);
        FactionModel sol = Mapper.getFaction("sol");
        player = game.addPlayer("sol-user", sol.getFactionName());
        player.setFaction(game, "sol");
        player.setColor("red");
        player.setUnitsOwned(new HashSet<>(sol.getUnits()));

        for (String position : MapSegment.positionsAround("000", 1)) {
            game.setTile(new Tile("19", position));
        }
        for (String position : MapSegment.positionsAround("1237", 1)) {
            game.setTile(new Tile("20", position));
        }
        // The player's only unit sits at the centre, so the far cluster at 1237 is unknown to them.
        game.getTileByPosition("000")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
    }

    @Test
    void fogViewOnlyOffersSegmentsWithAKnownSystem() {
        MapSegment.put(game, new MapSegment("core", "000", 1));
        MapSegment.put(game, new MapSegment("south", "1237", 1));

        assertEquals(List.of("core"), MapSegmentService.viewableNames(game, player.getUserID(), true));
        assertTrue(
                MapSegmentService.switchButtons(game, player.getUserID(), true).isEmpty(),
                "a single visible segment needs no switch buttons");

        game.getTileByPosition("1237")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        assertEquals(List.of("core", "south"), MapSegmentService.viewableNames(game, player.getUserID(), true));
        assertEquals(
                2,
                MapSegmentService.switchButtons(game, player.getUserID(), true).size());
    }

    @Test
    void separateFractureOffersMainAndFractureWhenTheGmDefinedNoSegments() {
        game.setTile(new Tile("21", "frac1"));
        game.getTileByPosition("frac1")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);

        assertEquals(
                List.of(MapSegment.MAIN, MapSegment.FRACTURE),
                MapSegmentService.viewableNames(game, player.getUserID(), true));
    }

    @Test
    void automaticSectorsOnlyShowTheOnesThePlayerKnows() {
        MapSegment.setAutoSectors(game, true);
        List<String> sectors =
                MapSegment.all(game).stream().map(MapSegment::name).toList();
        assertEquals(2, sectors.size());
        assertEquals(List.of(sectors.getFirst()), MapSegmentService.viewableNames(game, player.getUserID(), true));

        game.getTileByPosition("1237")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        assertEquals(sectors, MapSegmentService.viewableNames(game, player.getUserID(), true));
    }

    @Test
    void segmentTravelsThroughAButtonIdAndBack() {
        assertEquals("showMap", MapSegmentService.withSegment("showMap", null));
        assertEquals("showMap_ursa-major", MapSegmentService.withSegment("showMap", "ursa-major"));

        assertEquals("ursa-major", MapSegmentService.segmentFrom("showMap_ursa-major", "showMap"));
      assertNull(MapSegmentService.segmentFrom("showMap", "showMap"), "no segment in the id");
      assertNull(MapSegmentService.segmentFrom("showMap_Not Valid!", "showMap"), "invalid names are ignored");
    }

    @Test
    void classicMapLayoutOffersNoSectorButtons() {
        MapSegment.put(game, new MapSegment("core", "000", 1));
        MapSegment.put(game, new MapSegment("far", "1237", 1));
        game.getTileByPosition("1237")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        game.setFowOption(FOWOption.CLASSIC_MAP_LAYOUT, true);
        assertTrue(
                MapSegmentService.viewableNames(game, player.getUserID(), true).isEmpty());
    }

    @Test
    void nonPlayersGetNothingInAFoggedView() {
        MapSegment.put(game, new MapSegment("core", "000", 1));
        assertTrue(MapSegmentService.viewableNames(game, "stranger", true).isEmpty());
    }
}
