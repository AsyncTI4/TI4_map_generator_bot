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

// A mistyped count (e.g. 1314000146 infantry in pbd26171) once ran the bot out of memory. Adding units only stops
// at the per-holder sanity limits; reinforcements are not enforced here.
class AddUnitSanityLimitTest extends BaseTi4Test {

    private Game game;
    private Player letnev;
    private Tile tile;
    private Tile otherTile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("add-unit-sanity-limit-test");
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
    void unitsBeyondReinforcementsCanStillBePlaced() {
        int reinforcements = letnev.getUnitCap("ws");

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), (reinforcements + 3) + " warsun");

        assertThat(ButtonHelper.getNumberOfUnitsOnTheBoard(game, key(UnitType.Warsun)))
                .isEqualTo(reinforcements + 3);
    }

    @Test
    void plasticUnitsStopAt99PerHolder() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "300 dreadnought");

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Dreadnought)))
                .isEqualTo(SanityLimits.PLASTIC_UNITS);
    }

    @Test
    void infantryStopAt999PerHolder() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "1314000146 infantry");

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Infantry)))
                .isEqualTo(SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);
    }

    @Test
    void holderLimitsAreSeparateForEachHolder() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "999 infantry");
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "10 infantry mecatol");

        assertThat(tile.getUnitHolders().get("mr").getUnitCount(key(UnitType.Infantry)))
                .isEqualTo(10);
    }

    @Test
    void unitsWithoutAReinforcementSlotCanBePlaced() {
        // Plenary Orbital has no reinforcement position, so its unit cap reads as 0.
        assertThat(letnev.getUnitCap("plenaryorbital")).isZero();

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "plenaryorbital mecatol");

        assertThat(tile.getUnitHolders().get("mr").getUnitCount(key(UnitType.PlenaryOrbital)))
                .isEqualTo(1);
    }

    @Test
    void neutralUnitsIgnoreTheirReinforcements() {
        Player neutral = game.setupNeutralPlayer();
        int reinforcements = neutral.getUnitCap("dd");
        UnitKey neutralDestroyer = Units.getUnitKey(UnitType.Destroyer, neutral.getColorID());

        AddUnitService.addUnits(null, tile, game, neutral.getColor(), (reinforcements + 4) + " destroyer");

        assertThat(tile.getSpaceUnitHolder().getUnitCount(neutralDestroyer)).isEqualTo(reinforcements + 4);
    }

    @Test
    void movingUnitsStillStopAtTheHolderLimit() {
        tile.addUnit(Constants.SPACE, key(UnitType.Infantry), 990);
        otherTile.addUnit(Constants.SPACE, key(UnitType.Infantry), 5);
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnits(null, otherTile, game, letnev.getColor(), "5 infantry");

        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "500 infantry", removed);

        assertThat(tile.getSpaceUnitHolder().getUnitCount(key(UnitType.Infantry)))
                .isEqualTo(SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);
    }
}
