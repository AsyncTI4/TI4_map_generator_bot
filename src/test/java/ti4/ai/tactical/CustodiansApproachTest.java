package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// A transport too far from Mecatol Rex to land this action moves a system closer when that wins a round in the race
// for the custodians, and not when it gains nothing: the race matters only where a rival would otherwise arrive first.
class CustodiansApproachTest extends BaseTi4Test {

    private static final String EMPTY_SYSTEM = "46";
    private static final String STEP = "201";

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        board(2);
    }

    private void board(int infantryAtHome) {
        test = new AiTestGame();
        test.game.setRound(1);
        test.game.setPhaseOfGame("action");
        home = test.nekroHome();
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        test.place(EMPTY_SYSTEM, "101");
        test.place(EMPTY_SYSTEM, STEP);
        test.nekro.addTech("amd");
        test.nekro.setTg(6);
        test.nekro.setTacticalCC(3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, infantryAtHome);
        Tile rival = test.place(EMPTY_SYSTEM, "202");
        test.units(rival, "space", test.sol, UnitType.Carrier, 1);
        test.units(rival, "space", test.sol, UnitType.Infantry, 1);
        test.sol.setTg(6);
        test.sol.setTacticalCC(3);
    }

    private List<TacticalPlan> approachPlans() {
        return TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> plan.kind() == Kind.STAGE)
                .filter(plan -> plan.moves().stream()
                        .anyMatch(move -> AiTestGame.HOME.equals(move.origin()) && move.type() == UnitType.Carrier))
                .toList();
    }

    // Three systems out, Nekro (with a propulsion technology, so Gravity Drive is likely) would land in round 3 if it
    // waited, a round behind Sol. One system closer it lands in round 2 and races Sol for it.
    @Test
    void movesALoadedCarrierACloserSystemWhenThatWinsARound() {
        List<TacticalPlan> plans = approachPlans();

        assertThat(plans).isNotEmpty();
        TacticalPlan plan = plans.getFirst();
        assertThat(plan.target()).isEqualTo(STEP);
        assertThat(plan.moves())
                .anyMatch(move -> move.type() == UnitType.Carrier && move.count() == 1)
                .anyMatch(move -> move.type() == UnitType.Infantry && move.count() >= 1);
        assertThat(plan.landings()).isEmpty();
    }

    // With a carrier already parked a system closer (its command token on the system), the seat's own arrival is
    // settled: a second carrier moving there gains nothing.
    @Test
    void doesNotMoveASecondCarrierWhereTheFirstAlreadyWaits() {
        Tile parked = test.game.getTileByPosition(STEP);
        test.units(parked, "space", test.nekro, UnitType.Carrier, 1);
        test.units(parked, "space", test.nekro, UnitType.Infantry, 1);
        parked.addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(approachPlans()).isEmpty();
    }

    @Test
    void doesNotApproachOnceTheCustodiansAreGone() {
        test.game.getTileByPosition("000").getUnitHolders().get("mr").removeToken(Constants.CUSTODIAN_TOKEN_PNG);

        assertThat(approachPlans()).isEmpty();
    }

    // With no infantry at home there is nothing for the carrier to bring.
    @Test
    void doesNotApproachWithoutSpareGroundForces() {
        board(0);

        assertThat(approachPlans()).isEmpty();
    }

    // A lone rival destroyer in reach of home in round 1 cannot land and is no reason for a standing garrison, so the
    // last infantry is spare and rides along.
    @Test
    void aLoneDestroyerInReachOfHomeDoesNotPinTheLastInfantry() {
        board(1);
        Tile nextToHome = test.place(EMPTY_SYSTEM, AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(nextToHome, "space", test.sol, UnitType.Destroyer, 1);

        assertThat(approachPlans()).isNotEmpty();
    }
}
