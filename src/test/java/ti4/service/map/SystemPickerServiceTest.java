package ti4.service.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
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
import ti4.service.map.SystemPickerService.Area;
import ti4.service.map.SystemPickerService.Part;
import ti4.testUtils.BaseTi4Test;

class SystemPickerServiceTest extends BaseTi4Test {

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("system-picker-test");
        game.setFowMode(true);
        FactionModel sol = Mapper.getFaction("sol");
        player = game.addPlayer("sol-user", sol.getFactionName());
        player.setFaction(game, "sol");
        player.setColor("red");
        player.setUnitsOwned(new HashSet<>(sol.getUnits()));

        // Two 7-system clusters: one on the galaxy centre, one far south around 1237.
        for (String position : MapSegment.positionsAround("000", 1)) {
            game.setTile(new Tile("19", position));
        }
        for (String position : MapSegment.positionsAround("1237", 1)) {
            game.setTile(new Tile("20", position));
        }
        game.getTileByPosition("000")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
    }

    private static List<String> labels(List<Button> buttons) {
        return buttons.stream().map(Button::getLabel).toList();
    }

    private static List<String> ring(int ring) {
        Set<String> ringPositions = new HashSet<>(MapSegment.positionsAround("000", ring));
        ringPositions.removeAll(MapSegment.positionsAround("000", ring - 1));
        return List.copyOf(ringPositions);
    }

    @Test
    void withoutSectorsRingsCountFromTheCentreAndEmptyRingsAreSkipped() {
        assertEquals(
                List.of("Centre (1)", "Ring #1 (6)", "Ring #11 (1)", "Ring #12 (3)", "Ring #13 (3)"),
                labels(SystemPickerService.firstStepButtons(player, game)));
    }

    @Test
    void sectorsTheyKnowAreOfferedAndUnknownOnesFallIntoTheMainMap() {
        MapSegment.put(game, new MapSegment("core", "000", 1));
        MapSegment.put(game, new MapSegment("south", "1237", 1));

        // "south" is unknown to the player, so its systems stay reachable under "main" without naming the sector.
        assertEquals(
                List.of("Map: main (7)", "Map: core (7)"), labels(SystemPickerService.firstStepButtons(player, game)));

        game.getTileByPosition("1237")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        assertEquals(
                List.of("Map: core (7)", "Map: south (7)"), labels(SystemPickerService.firstStepButtons(player, game)));
    }

    @Test
    void automaticSectorsCountRingsFromTheirOwnMiddle() {
        MapSegment.setAutoSectors(game, true);
        game.getTileByPosition("1237")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);

        List<Area> areas = SystemPickerService.areas(game, player);
        assertEquals(2, areas.size());
        Area south = areas.stream()
                .filter(area -> area.positions().contains("1237"))
                .findFirst()
                .orElseThrow();
        assertEquals("1237", south.centre());
        assertEquals(
                Set.of("0", "1"),
                SystemPickerService.byRing(south, List.copyOf(south.positions()))
                        .keySet());
    }

    @Test
    void largeRingsSplitIntoHalvesThenIntoSidesAndNothingIsLost() {
        Map<Part, List<String>> ringFive = SystemPickerService.split("000", ring(5));
        assertEquals(Set.of(Part.W, Part.E), ringFive.keySet());
        assertCoversAndFits(ring(5), ringFive);

        Map<Part, List<String>> ringTwelve = SystemPickerService.split("000", ring(12));
        assertEquals(Set.of(Part.N, Part.NE, Part.SE, Part.S, Part.SW, Part.NW), ringTwelve.keySet());
        assertCoversAndFits(ring(12), ringTwelve);
    }

    private static void assertCoversAndFits(List<String> ring, Map<Part, List<String>> parts) {
        Set<String> covered = new HashSet<>();
        parts.values().forEach(covered::addAll);
        assertEquals(Set.copyOf(ring), covered, "every system of the ring is in some part");
        assertTrue(parts.values().stream().allMatch(part -> part.size() <= 23), "every part fits one message");
    }
}
