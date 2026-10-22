package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// aiwatch9, round 2: Letnev had a carrier and infantry one jump from Mecatol Rex and took two planets elsewhere, and
// Hacan took the custodians. The seat is paid for Amass Wealth at status and for the custodians now, and could not pay
// for both, so the objective's reserve left the custodians out of reach. The custodians come first, then an objective.
class CustodiansReserveTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final String ONE_AWAY = "101";
    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private Tile staging;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(2);
        test.aiIsActive("action");
        test.nekroHome();
        Tile rex = test.place("18", MECATOL);
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        staging = test.place("26", ONE_AWAY);
        test.units(staging, "space", test.nekro, UnitType.Carrier, 1);
        test.units(staging, "space", test.nekro, UnitType.Infantry, 2);
        test.place("10", "302");
        test.place("35", "303");
        for (String planet : new String[] {"arcprime", "wrenterra", "bereg", "lirtaiv"}) test.nekro.addPlanet(planet);
        test.nekro.setTg(3);
        test.nekro.setTacticalCC(3);
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
    }

    @Test
    void anObjectiveAloneWouldTakeTheWholeWallet() {
        assertThat(Wallet.of(test.game, test.nekro).canPay(SpendCost.each(3))).isTrue();
        assertThat(Wallet.of(test.game, test.nekro).canPay(SpendCost.each(3).plus(SpendCost.influence(6))))
                .isFalse();
    }

    @Test
    void reservesTheCustodiansBeforeTheObjectiveWhenTheSeatWinsTheRace() {
        assertThat(ScoringReserve.custodiansReserve(test.game, test.nekro)).isEqualTo(SpendCost.influence(6));
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
        assertThat(ScoringReserve.of(test.game, test.nekro)).isEqualTo(SpendCost.influence(6));
    }

    @Test
    void plansTheTakingOfMecatolRexDespiteTheObjective() {
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, MECATOL))
                .hasValueSatisfying(plan -> assertThat(plan.kind()).isEqualTo(Kind.EXPAND));
    }

    @Test
    void reservesNothingForTheCustodiansWhenNoShipCanLandThisRound() {
        staging.removeAllUnits(test.nekro.getColor());

        assertThat(ScoringReserve.custodiansReserve(test.game, test.nekro)).isEqualTo(SpendCost.NONE);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isPresent();
    }

    @Test
    void reservesNothingWhenAnotherSeatLandsFirstAndTheSeatHoldsNoImperial() {
        test.units(test.place("26", "102"), "space", test.sol, UnitType.Carrier, 1);
        test.units(test.game.getTileByPosition("102"), "space", test.sol, UnitType.Infantry, 2);
        test.sol.setTg(6);
        test.sol.setTacticalCC(3);
        test.sol.addSC(1);
        test.nekro.addSC(6);

        assertThat(ScoringReserve.custodiansReserve(test.game, test.nekro)).isEqualTo(SpendCost.NONE);
    }

    @Test
    void reservesForTheCustodiansWhenTheSeatHoldsTheUnplayedImperialEvenIfBehind() {
        test.units(test.place("26", "102"), "space", test.sol, UnitType.Carrier, 1);
        test.units(test.game.getTileByPosition("102"), "space", test.sol, UnitType.Infantry, 2);
        test.sol.setTg(6);
        test.sol.setTacticalCC(3);
        test.sol.addSC(1);
        test.nekro.addSC(IMPERIAL);

        assertThat(ScoringReserve.custodiansReserve(test.game, test.nekro)).isEqualTo(SpendCost.influence(6));
    }
}
