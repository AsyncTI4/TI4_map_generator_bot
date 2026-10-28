package ti4.service.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.SanityLimits;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.unit.RemoveUnitService.RemovedUnit;
import ti4.testUtils.BaseTi4Test;

class AddUnitSupplyLimitTest extends BaseTi4Test {

    private Game game;
    private Player letnev;
    private Tile tile;
    private Tile otherTile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("add-unit-supply-limit-test");
        game.setCcNPlasticLimit(false);
        tile = new Tile("18", "000");
        otherTile = new Tile("20", "102");
        game.setTile(tile);
        game.setTile(otherTile);
        letnev = game.addPlayer("111", "letnev");
        letnev.setFaction(game, "letnev");
        letnev.setColor("black");
        letnev.setUnitsOwned(new HashSet<>(Mapper.getFaction("letnev").getUnits()));
    }

    private UnitKey key(UnitType type) {
        return Units.getUnitKey(type, letnev.getColorID());
    }

    @Test
    void cannotSpawnMoreWarSunsThanReinforcementsHold() {
        int reinforcements = letnev.getUnitCap("ws");

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "50 warsun");

        assertThat(ButtonHelper.getNumberOfUnitsOnTheBoard(game, key(UnitType.Warsun)))
                .isEqualTo(reinforcements);
    }

    @Test
    void unitsAlreadyOnTheBoardUseUpReinforcements() {
        int reinforcements = letnev.getUnitCap("dn");
        otherTile.addUnit(Constants.SPACE, key(UnitType.Dreadnought), reinforcements - 1);

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "3 dreadnought");

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Dreadnought)))
                .isEqualTo(1);
    }

    @Test
    void raisedReinforcementsStopAt99() {
        letnev.setUnitCap("dn", 500);

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "300 dreadnought");

        assertThat(ButtonHelper.getNumberOfUnitsOnTheBoard(game, key(UnitType.Dreadnought)))
                .isEqualTo(SanityLimits.OTHER_UNITS_OF_A_KIND);
    }

    @Test
    void infantryStopAt999PerSystemAcrossItsPlanets() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "600 infantry mecatol");
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "600 infantry");

        int inTile = tile.getUnitHolders().values().stream()
                .mapToInt(unitHolder -> unitHolder.getUnitCount(key(UnitType.Infantry)))
                .sum();
        assertThat(inTile).isEqualTo(SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);
    }

    @Test
    void infantryLimitIsPerSystemNotPerBoard() {
        AddUnitService.addUnits(null, otherTile, game, letnev.getColor(), "999 infantry");

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "10 infantry");

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Infantry)))
                .isEqualTo(10);
    }

    // /move_units takes a separate destination list, so it can ask for more units than it removed.
    @Test
    void movingCannotSpawnExtraUnitsBeyondReinforcements() {
        int reinforcements = letnev.getUnitCap("dn");
        otherTile.addUnit(Constants.SPACE, key(UnitType.Dreadnought), reinforcements);
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnits(null, otherTile, game, letnev.getColor(), "2 dreadnought");

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "50 dreadnought", removed);

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Dreadnought)))
                .isEqualTo(2);
        assertThat(ButtonHelper.getNumberOfUnitsOnTheBoard(game, key(UnitType.Dreadnought)))
                .isEqualTo(reinforcements);
    }

    @Test
    void movingAFleetThatIsAlreadyOverTheLimitKeepsEveryUnit() {
        int reinforcements = letnev.getUnitCap("dn");
        otherTile.addUnit(Constants.SPACE, key(UnitType.Dreadnought), reinforcements + 3);
        List<RemovedUnit> removed = RemoveUnitService.removeUnits(
                null, otherTile, game, letnev.getColor(), (reinforcements + 3) + " dreadnought");

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), (reinforcements + 3) + " dreadnought", removed);

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Dreadnought)))
                .isEqualTo(reinforcements + 3);
    }

    @Test
    void movingInfantryCannotPushASystemPast999() {
        tile.addUnit(Constants.SPACE, key(UnitType.Infantry), 990);
        otherTile.addUnit(Constants.SPACE, key(UnitType.Infantry), 5);
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnits(null, otherTile, game, letnev.getColor(), "5 infantry");

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "500 infantry", removed);

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Infantry)))
                .isEqualTo(SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);
    }
}
