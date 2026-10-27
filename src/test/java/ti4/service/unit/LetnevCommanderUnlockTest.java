package ti4.service.unit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class LetnevCommanderUnlockTest extends BaseTi4Test {

    private static final String COMMANDER = "letnevcommander";
    private static final int FLEET_TOKENS = 5;

    private Game game;
    private Player letnev;
    private Tile tile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("letnev-commander-unlock-test");
        game.setCcNPlasticLimit(true);
        tile = new Tile("19", "101");
        game.setTile(tile);
        letnev = game.addPlayer("111", "letnev");
        letnev.setFaction(game, "letnev");
        letnev.setColor("black");
        letnev.setUnitsOwned(new HashSet<>(Mapper.getFaction("letnev").getUnits()));
        letnev.setFleetCC(FLEET_TOKENS);
        letnev.addLeader(COMMANDER);
    }

    @Test
    void addingTheFifthNonFighterShipUnlocksTheCommander() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "4 cruiser");
        assertFalse(letnev.hasLeaderUnlocked(COMMANDER));

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "1 cruiser");

        assertTrue(letnev.hasLeaderUnlocked(COMMANDER));
    }

    @Test
    void readingTheFleetAndCapacityNeverUnlocksTheCommander() {
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, letnev.getColorID()), 5);

        int[] counts = ButtonHelper.fleetAndCapacity(letnev, game, tile, false);

        assertFalse(letnev.hasLeaderUnlocked(COMMANDER));
        assertArrayEquals(ButtonHelper.fleetAndCapacity(letnev, game, tile, false), counts);
    }

    @Test
    void theCheckThatPingsStillUnlocksTheCommanderAndReturnsTheSameCounts() {
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, letnev.getColorID()), 5);
        int[] counts = ButtonHelper.fleetAndCapacity(letnev, game, tile, false);

        int[] checked = ButtonHelper.checkFleetAndCapacity(letnev, game, tile, false, false);

        assertTrue(letnev.hasLeaderUnlocked(COMMANDER));
        assertArrayEquals(counts, checked);
    }
}
