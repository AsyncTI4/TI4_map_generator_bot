package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class DestroyGreatestShipBonusTest extends BaseTi4Test {

    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    private AiTestGame test;
    private Tile target;
    private List<UnitMove> ships;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.nekroHome();
        target = test.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 2);
        ships = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
        test.nekro.setSecret("dtgs");
    }

    private double bonus() {
        return TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, test.sol, ships, true, false);
    }

    // The bonus already depends on the odds of winning, so a concrete strike on a flagship or war sun earns the whole
    // point rather than a patient share of it.
    @Test
    void aStrikeOnAnEnemyFlagshipIsWorthTheWholePoint() {
        test.units(target, "space", test.sol, UnitType.Flagship, 1);

        assertThat(bonus()).isEqualTo(VP);
    }

    @Test
    void aStrikeOnAnEnemyWarSunIsWorthTheWholePoint() {
        test.units(target, "space", test.sol, UnitType.Warsun, 1);

        assertThat(bonus()).isEqualTo(VP);
    }

    @Test
    void aStrikeWithoutAHeavyShipIsWorthNothing() {
        test.units(target, "space", test.sol, UnitType.Dreadnought, 1);

        assertThat(bonus()).isZero();
    }
}
