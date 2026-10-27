package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * After an extortion demand, Nekro's attack on Sol's Lodor (next door, one cruiser) is either spared for the round
 * (the demand was paid) or committed to (refused): the committed attack scores higher by the demand's worth.
 */
class ExtortionPlansTest extends BaseTi4Test {

    private AiTestGame test;
    private String lodor;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        test.nekro.setFleetCC(8);
        lodor = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile tile = test.place("26", lodor);
        test.sol.addPlanet("lodor");
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
    }

    private Optional<TacticalPlan> attackOnLodor() {
        return TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> plan.target().equals(lodor) && plan.kind() == Kind.ATTACK)
                .findFirst();
    }

    @Test
    void aSparedSystemIsNotAttackedThisRound() {
        assertThat(attackOnLodor()).isPresent();

        ExtortionPlans.paidBy(test.game, test.nekro, test.sol);
        ExtortionPlans.spare(test.game, test.nekro, lodor);

        assertThat(attackOnLodor()).isEmpty();
        test.game.setRound(4);
        assertThat(attackOnLodor()).isPresent();
    }

    // Once Sol has paid Nekro this round, attacking Sol anywhere is worth half as much to Nekro for the rest of the
    // round; the next round it is worth its full value again.
    @Test
    void aPayerIsWorthHalfAsMuchToAttackThisRound() {
        double before = attackOnLodor().orElseThrow().score();

        ExtortionPlans.paidBy(test.game, test.nekro, test.sol);

        assertThat(attackOnLodor().orElseThrow().score()).isEqualTo(before * ExtortionPlans.PAID_PAYOFF);
        test.game.setRound(4);
        assertThat(attackOnLodor().orElseThrow().score()).isEqualTo(before);
    }

    // Sol, having paid, no longer fears an attack from Nekro on the system it bought off, fears one elsewhere half as
    // much this round, and as usual next round.
    @Test
    void thePayerTrustsTheDealForTheRound() {
        Tile tile = test.game.getTileByPosition(lodor);
        assertThat(ExtortionPlans.fearOf(test.game, test.nekro, test.sol, tile)).isEqualTo(1.0);

        ExtortionPlans.paidBy(test.game, test.nekro, test.sol);
        assertThat(ExtortionPlans.fearOf(test.game, test.nekro, test.sol, tile)).isEqualTo(ExtortionPlans.PAID_FEAR);

        ExtortionPlans.spare(test.game, test.nekro, lodor);
        assertThat(ExtortionPlans.fearOf(test.game, test.nekro, test.sol, tile)).isZero();

        test.game.setRound(4);
        assertThat(ExtortionPlans.fearOf(test.game, test.nekro, test.sol, tile)).isEqualTo(1.0);
    }

    // Sol paid on 0 points, so the deal held. Once Sol (now on 9 with Imperial and Mecatol to take) could win this
    // round, all deals are off: the spared system may be attacked, the attack is worth its full value, and Sol fears
    // it again.
    @Test
    void allDealsAreOffOnceThePayerCanWin() {
        double before = attackOnLodor().orElseThrow().score();
        ExtortionPlans.paidBy(test.game, test.nekro, test.sol);
        ExtortionPlans.spare(test.game, test.nekro, lodor);
        assertThat(attackOnLodor()).isEmpty();

        for (int card = 1; card <= 8; card++) test.game.setScTradeGood(card, 0);
        test.sol.addSC(8);
        test.points(test.sol, 9);

        assertThat(ExtortionPlans.dealOff(test.game, test.nekro, test.sol)).isTrue();
        assertThat(attackOnLodor()).isPresent();
        assertThat(attackOnLodor().orElseThrow().score()).isGreaterThan(before * ExtortionPlans.PAID_PAYOFF);
        assertThat(ExtortionPlans.fearOf(test.game, test.nekro, test.sol, test.game.getTileByPosition(lodor)))
                .isEqualTo(1.0);
    }

    @Test
    void aRefusedDemandMakesTheAttackWorthMore() {
        double before = attackOnLodor().orElseThrow().score();

        ExtortionPlans.commit(test.game, test.nekro, lodor, 4);

        assertThat(attackOnLodor().orElseThrow().score()).isEqualTo(before + 4);
    }
}
