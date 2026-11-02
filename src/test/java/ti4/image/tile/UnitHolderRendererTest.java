package ti4.image.tile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.HashSet;
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
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class UnitHolderRendererTest extends BaseTi4Test {

    private Game game;
    private Tile tile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("unit-holder-renderer-test");
        tile = new Tile("19", "101");
        game.setTile(tile);
    }

    private Player seat(String userId, String faction, String color, boolean ownsUnits) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(
                ownsUnits ? new HashSet<>(Mapper.getFaction(faction).getUnits()) : new HashSet<>());
        return player;
    }

    private void drawUnits() {
        TileLayers.of(game, null, DisplayType.all).draw(tile, TileStep.Units);
    }

    @Test
    void unitsWithoutAnOwnerAreLoggedOncePerRenderAndNeverMessagedToTheGameChannel() {
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, "blu"), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, "blu"), 1);

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<BotLogger> log = mockStatic(BotLogger.class, Mockito.CALLS_REAL_METHODS)) {
            drawUnits();

            messages.verifyNoInteractions();
            log.verify(() -> BotLogger.warning(any(LogOrigin.class), contains("Could not find owner")), times(1));
        }
    }

    @Test
    void unitsWithoutAModelAreLoggedOncePerRenderAndNeverMessagedToThePlayer() {
        Player owner = seat("111", "sol", "blue", false);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, owner.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, owner.getColorID()), 1);

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<BotLogger> log = mockStatic(BotLogger.class, Mockito.CALLS_REAL_METHODS)) {
            drawUnits();

            messages.verifyNoInteractions();
            log.verify(() -> BotLogger.warning(any(LogOrigin.class), contains("No unit model found")), times(1));
        }
    }

    @Test
    void unitsWithAnOwnerAndAModelProduceNoWarning() {
        Player owner = seat("111", "sol", "blue", true);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, owner.getColorID()), 1);

        try (MockedStatic<BotLogger> log = mockStatic(BotLogger.class, Mockito.CALLS_REAL_METHODS)) {
            drawUnits();

            log.verify(() -> BotLogger.warning(any(LogOrigin.class), contains("while rendering")), never());
        }
    }
}
