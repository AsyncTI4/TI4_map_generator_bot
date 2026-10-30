package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.CombatModifiers;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.secrets.ActionSecretTiming;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class BraveTheVoidBonusTest extends BaseTi4Test {

    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    private AiTestGame test;
    private List<UnitMove> ships;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 2);
        ships = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
        test.nekro.setSecret("btv");
    }

    private Tile enemyDestroyerIn(String tileId) {
        Tile tile = test.place(tileId, AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(tile, "space", test.sol, UnitType.Destroyer, 1);
        return tile;
    }

    private double bonus(Tile target) {
        return TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, test.sol, ships, true, false);
    }

    // Round one is patient: a secret that is not pursued yet is credited a quarter of its point. Brave the Void's
    // bonus exists only on a concrete strike into an anomaly, so the strike is worth the whole point regardless.
    @Test
    void aStrikeIntoAnAnomalyIsWorthTheWholePointEvenWhileThePlayerIsPatient() {
        test.game.setRound(1);
        Tile rift = enemyDestroyerIn("41");
        assertThat(ActionSecretTiming.pursueNow(test.game, test.nekro, "btv")).isFalse();

        assertThat(bonus(rift)).isEqualTo(VP);
    }

    @Test
    void aStrikeOutsideAnyAnomalyIsWorthNothing() {
        assertThat(bonus(enemyDestroyerIn("46"))).isZero();
    }

    @Test
    void aWormholeAloneIsNotAnAnomaly() {
        assertThat(bonus(enemyDestroyerIn("39"))).isZero();
    }

    // LRR 59.3: ships in a nebula whose owner is not the active player get +1 to their combat rolls.
    @Test
    void theDefenderInANebulaGetsPlusOneInTheStrikeEstimate() {
        Tile nebula = enemyDestroyerIn("42");
        assertThat(nebula.isNebula(test.game)).isTrue();
        Map<UnitType, Integer> destroyer = Map.of(UnitType.Destroyer, 1);
        Map<UnitType, Integer> dreadnoughts = Map.of(UnitType.Dreadnought, 2);

        Map<UnitType, Integer> defending = CombatModifiers.hitModifiers(
                test.game,
                nebula,
                nebula.getSpaceUnitHolder(),
                new Side(test.sol, destroyer),
                new Side(test.nekro, dreadnoughts));
        Map<UnitType, Integer> attacking = CombatModifiers.hitModifiers(
                test.game,
                nebula,
                nebula.getSpaceUnitHolder(),
                new Side(test.nekro, dreadnoughts),
                new Side(test.sol, destroyer));

        assertThat(defending.get(UnitType.Destroyer)).isEqualTo(1);
        assertThat(attacking.get(UnitType.Dreadnought)).isZero();
    }
}
