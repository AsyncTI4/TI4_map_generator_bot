package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.FogEvents;
import ti4.testUtils.GameStateSnapshot;

class MapPaintPurityTest extends BaseTi4Test {

    private static final List<DisplayType> DISPLAY_TYPES = List.of(
            DisplayType.all,
            DisplayType.stats,
            DisplayType.map,
            DisplayType.wormholes,
            DisplayType.traits,
            DisplayType.techskips,
            DisplayType.attachments);

    private Game game;
    private Player first;
    private Player second;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("map-paint-purity-test");
        game.setOwnerID("111");
        game.setOwnerName("owner");
        game.setCcNPlasticLimit(true);
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
        game.setTile(new Tile("25", "101"));
        first = seat("111", "sol", "blue");
        second = seat("222", "hacan", "yellow");
        first.setFleetCC(1);
        first.addPlanet("quann");
        first.addDebtTokens(second.getColor(), 2, "favours");
        second.addDebtTokens(first.getColor(), 1, Constants.DEBT_DEFAULT_POOL);
        Tile quann = game.getTileByPosition("101");
        quann.addUnit("quann", Units.getUnitKey(UnitType.Infantry, first.getColorID()), 3);
        quann.addUnit("quann", Units.getUnitKey(UnitType.Spacedock, first.getColorID()), 1);
        quann.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, first.getColorID()), 3);
        game.getTileByPosition("301")
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, second.getColorID()), 1);
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        player.setCommoditiesBase(player.getFactionSetupInfo().getCommodities());
        return player;
    }

    private void assertPaintLeavesTheGameAlone(DisplayType type, GenericInteractionCreateEvent event) {
        RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, type, event, null));
        List<String> stateBefore = GameStateSnapshot.saveText(game);
        String fogBefore = GameStateSnapshot.fogFlags(game);
        Map<String, Integer> distancesBefore = Map.copyOf(game.getTileDistances());

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class, Mockito.CALLS_REAL_METHODS)) {
            MapRenderer.paint(context, RenderTimings.disabled());

            messages.verifyNoInteractions();
        }

        assertEquals(
                List.of(),
                GameStateSnapshot.changes(stateBefore, GameStateSnapshot.saveText(game)),
                "game state after paint of " + type);
        assertEquals(fogBefore, GameStateSnapshot.fogFlags(game), "fog flags after paint of " + type);
        assertEquals(distancesBefore, game.getTileDistances());
    }

    @Test
    void paintingAPublicMapChangesNothingAndSendsNothing() {
        for (DisplayType type : DISPLAY_TYPES) {
            assertPaintLeavesTheGameAlone(type, null);
        }
    }

    @Test
    void paintingAFogMapChangesNothingAndSendsNothing() {
        game.setFowMode(true);
        for (DisplayType type : DISPLAY_TYPES) {
            assertPaintLeavesTheGameAlone(type, FogEvents.eventIn("111", "fog-111-private"));
        }
    }

    @Test
    void paintingAFogMapWithoutAViewerChangesNothingAndSendsNothing() {
        game.setFowMode(true);
        for (DisplayType type : DISPLAY_TYPES) {
            assertPaintLeavesTheGameAlone(type, FogEvents.eventIn("999", "gm-private"));
        }
    }

    @Test
    void paintingWithShownDistancesChangesNothingUntilTheRenderCompletes() {
        game.setTileDistances(new java.util.HashMap<>(Map.of("000", 1, "101", 2)));

        assertPaintLeavesTheGameAlone(DisplayType.all, null);
    }
}
