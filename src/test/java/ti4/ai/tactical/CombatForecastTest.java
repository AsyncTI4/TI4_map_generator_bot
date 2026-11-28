package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.CombatForecast.RoundModifier;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The swing is the change in the seat's chance to win the combat, times what is at stake.
class CombatForecastTest extends BaseTi4Test {

    private static final String SPACE = "space";

    private AiTestGame test;
    private String position;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        test.aiIsActive("action");
        test.game.setActiveSystem(position);
    }

    private void fleets(UnitType nekroType, int nekro, UnitType solType, int sol) {
        test.units(tile, SPACE, test.nekro, nekroType, nekro);
        test.units(tile, SPACE, test.sol, solType, sol);
    }

    private double moraleBoost(ti4.game.Player seat) {
        return CombatForecast.firstRoundSwing(test.game, seat, position, SPACE, RoundModifier.allUnits(1));
    }

    @Test
    void aBoostToAllDiceIsWorthSomethingInAnEvenFight() {
        fleets(UnitType.Cruiser, 2, UnitType.Cruiser, 2);

        assertThat(moraleBoost(test.nekro)).isGreaterThan(0.5);
        assertThat(moraleBoost(test.sol)).isGreaterThan(0.5);
    }

    @Test
    void aBoostIsWorthLittleInAFightAlreadyDecided() {
        fleets(UnitType.Dreadnought, 6, UnitType.Fighter, 1);

        assertThat(moraleBoost(test.nekro)).isBetween(0.0, 0.05);
    }

    @Test
    void aBoostIsWorthMoreInACloseFightThanInALopsidedOne() {
        fleets(UnitType.Cruiser, 2, UnitType.Cruiser, 2);
        double close = moraleBoost(test.nekro);
        test.units(tile, SPACE, test.nekro, UnitType.Dreadnought, 4);

        assertThat(moraleBoost(test.nekro)).isLessThan(close);
    }

    @Test
    void aFighterBoostCountsOnlyFighters() {
        fleets(UnitType.Cruiser, 2, UnitType.Cruiser, 2);
        RoundModifier fighters = RoundModifier.fighters(2);
        assertThat(CombatForecast.firstRoundSwing(test.game, test.nekro, position, SPACE, fighters))
                .isZero();

        test.units(tile, SPACE, test.nekro, UnitType.Fighter, 3);

        assertThat(CombatForecast.firstRoundSwing(test.game, test.nekro, position, SPACE, fighters))
                .isGreaterThan(0.0);
    }

    @Test
    void thereIsNoSwingWithoutACombat() {
        test.units(tile, SPACE, test.nekro, UnitType.Cruiser, 2);

        assertThat(moraleBoost(test.nekro)).isZero();
        assertThat(CombatForecast.repairSwing(test.game, test.nekro, position, SPACE))
                .isZero();
    }

    @Test
    void repairsAreWorthNothingWithoutDamagedUnits() {
        fleets(UnitType.Dreadnought, 2, UnitType.Dreadnought, 2);

        assertThat(CombatForecast.repairSwing(test.game, test.nekro, position, SPACE))
                .isZero();
    }

    @Test
    void repairingDamagedDreadnoughtsIsWorthSomethingInACloseFight() {
        fleets(UnitType.Dreadnought, 2, UnitType.Dreadnought, 2);
        tile.addUnitDamage(SPACE, Units.getUnitKey(UnitType.Dreadnought, test.nekro.getColor()), 2);

        assertThat(CombatForecast.repairSwing(test.game, test.nekro, position, SPACE))
                .isGreaterThan(0.5);
        assertThat(CombatForecast.repairSwing(test.game, test.sol, position, SPACE))
                .isZero();
    }

    @Test
    void repairsAreWorthLittleWhenTheFightIsLostAnyway() {
        fleets(UnitType.Dreadnought, 1, UnitType.Dreadnought, 8);
        tile.addUnitDamage(SPACE, Units.getUnitKey(UnitType.Dreadnought, test.nekro.getColor()), 1);

        assertThat(CombatForecast.repairSwing(test.game, test.nekro, position, SPACE))
                .isBetween(0.0, 0.5);
    }
}
