package ti4.service.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.GalaxyNames;
import ti4.image.MapSegment;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.service.map.SystemPickerService.Area;
import ti4.service.map.SystemPickerService.Part;
import ti4.service.option.FOWOptionService.FOWOption;
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
    void unknownSectorsAreNeverOffered() {
        MapSegment.put(game, new MapSegment("core", "000", 1));
        MapSegment.put(game, new MapSegment("south", "1237", 1));

        // "south" is unknown, so neither its name nor its systems (not even pooled under "main") may show up.
        assertEquals(List.of("Centre (1)", "Ring #1 (6)"), labels(SystemPickerService.firstStepButtons(player, game)));

        game.getTileByPosition("1237")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        assertEquals(
                List.of("Map: core (7)", "Map: south (7)"), labels(SystemPickerService.firstStepButtons(player, game)));
    }

    @Test
    void sectorsOnAnExtraGalaxyAreOfferedWithTheGalaxyName() {
        MapSegment.put(game, new MapSegment("core", "000", 1));
        game.setTile(new Tile("19", "a000"));
        game.setTile(new Tile("19", "a301"));
        MapSegment.put(game, new MapSegment("outpost", "a000", 1));
        game.getTileByPosition("a000")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        game.getTileByPosition("a301")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);

        // Labels name the map; the button ids keep the internal sector names.
        List<Button> buttons = SystemPickerService.firstStepButtons(player, game);
        String galaxyA = GalaxyNames.name(game, "a");
        assertTrue(
                labels(buttons).containsAll(List.of("Map: " + galaxyA + " / outpost (1)", "Map: " + galaxyA + " (1)")));
        assertTrue(buttons.stream().anyMatch(button -> button.getCustomId().endsWith("systemPick_board-a")));
    }

    @Test
    void unseenSystemsInAKnownSectorAreNotOffered() {
        for (String position : MapSegment.positionsAround("000", 3)) {
            if (game.getTileByPosition(position) == null) {
                game.setTile(new Tile("19", position));
            }
        }
        MapSegment.put(game, new MapSegment("core", "000", 3));

        // The carrier on 000 sees only the centre and ring 1; rings 2 and 3 stay hidden.
        assertEquals(List.of("Centre (1)", "Ring #1 (6)"), labels(SystemPickerService.firstStepButtons(player, game)));

        // A remembered fog tile counts as known.
        String remembered = ring(3).getFirst();
        player.addFogTile("19", remembered, "");
        assertEquals(
                List.of("Centre (1)", "Ring #1 (6)", "Ring #3 (1)"),
                labels(SystemPickerService.firstStepButtons(player, game)));
    }

    @Test
    void unknownSystemButtonsAreDroppedOnlyOnSegmentedMaps() {
        List<Button> buttons = new ArrayList<>(List.of(
                Buttons.green("ringTile_000", "seen"),
                Buttons.green("ringTile_1237", "unseen"),
                Buttons.green("ring_corners", "Corners")));

        SystemPickerService.dropUnknownSystems(buttons, player, game);
        assertEquals(List.of("seen", "unseen", "Corners"), labels(buttons));

        MapSegment.put(game, new MapSegment("core", "000", 1));
        SystemPickerService.dropUnknownSystems(buttons, player, game);
        assertEquals(List.of("seen"), labels(buttons));
    }

    @Test
    void classicMapLayoutIgnoresSegments() {
        MapSegment.put(game, new MapSegment("core", "000", 1));
        MapSegment.put(game, new MapSegment("south", "1237", 1));
        game.setFowOption(FOWOption.CLASSIC_MAP_LAYOUT, true);

        assertEquals(
                List.of("Centre (1)", "Ring #1 (6)", "Ring #11 (1)", "Ring #12 (3)", "Ring #13 (3)"),
                labels(SystemPickerService.firstStepButtons(player, game)));
    }

    @Test
    void blindTileIsOfferedOnlyOnSegmentedFogMaps() {
        // Unseen systems are no longer listed on segmented maps, so Blind Tile is the way to reach them.
        assertTrue(possibleRingIds().stream().noneMatch(id -> id.contains("blindTileSelection")));

        MapSegment.put(game, new MapSegment("core", "000", 1));
        assertTrue(possibleRingIds().stream().anyMatch(id -> id.contains("blindTileSelection")));
    }

    private List<String> possibleRingIds() {
        return ButtonHelper.getPossibleRings(player, game).stream()
                .map(Button::getCustomId)
                .toList();
    }

    @Test
    void nonFogGamesKeepNumberedRings() {
        game.setFowMode(false);
        MapSegment.put(game, new MapSegment("core", "000", 1));

        List<String> ids = ButtonHelper.getPossibleRings(player, game).stream()
                .map(Button::getCustomId)
                .toList();
        assertTrue(ids.stream().anyMatch(id -> id.endsWith("ring_1")));
        assertTrue(ids.stream().anyMatch(id -> id.endsWith("ring_corners")));
        assertTrue(ids.stream().noneMatch(id -> id.contains("systemPick_")));
    }

    @Test
    void sectorRingsUseTheMapsOwnRingNumbers() {
        MapSegment.setAutoSectors(game, true);
        game.getTileByPosition("1237")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);

        Area south = SystemPickerService.areas(game, player).stream()
                .filter(area -> area.positions().contains("1237"))
                .findFirst()
                .orElseThrow();

        // The south sector spans map rings 11-13; buttons name those rings, not rings counted from its middle.
        assertEquals(
                List.of("11", "12", "13"),
                List.copyOf(SystemPickerService.byRing(List.copyOf(south.positions()))
                        .keySet()));
    }

    @Test
    void classicLayoutKeepsEachGalaxyInItsOwnArea() {
        game.setFowOption(FOWOption.CLASSIC_MAP_LAYOUT, true);
        game.setTile(new Tile("19", "a301"));

        List<Area> areas = SystemPickerService.areas(game, player);

        assertEquals(2, areas.size(), "main and map A, never mixed");
        assertTrue(areas.stream()
                .noneMatch(area -> area.positions().contains("a301")
                        && area.positions().stream().anyMatch(position -> !position.startsWith("a"))));
    }

    @Test
    void extraGalaxiesCountRingsWithinThatGalaxy() {
        assertEquals("0", SystemPickerService.mapRing("000"));
        assertEquals("5", SystemPickerService.mapRing("501"));
        assertEquals("0", SystemPickerService.mapRing("a000"));
        assertEquals("2", SystemPickerService.mapRing("a204"));
        assertEquals("x", SystemPickerService.mapRing("tl"));
        assertEquals("x", SystemPickerService.mapRing("frac3"));
        assertEquals("a000", SystemPickerService.galaxyCentre(List.of("a501", "a502")));
        assertEquals("000", SystemPickerService.galaxyCentre(List.of("501")));
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
