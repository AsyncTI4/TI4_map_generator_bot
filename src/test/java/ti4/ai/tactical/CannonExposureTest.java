package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

class CannonExposureTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Tile home;
    private String target;
    private Tile solHome;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        test.sol.removeTech("amd");
        target = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .findFirst()
                .orElseThrow();
        test.place("19", target);
        solHome = test.solHomeBeside(target);
    }

    private void solCoversTheTarget(int pds) {
        test.sol.removeOwnedUnitByID("pds");
        test.sol.addOwnedUnitByID("pds2");
        test.units(solHome, "jord", test.sol, UnitType.Pds, pds);
    }

    private void fleet(int cruisers) {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        if (cruisers > 0) test.units(home, "space", test.nekro, UnitType.Cruiser, cruisers);
        test.units(home, "space", test.nekro, UnitType.Infantry, 1);
    }

    private Optional<TacticalPlan> expansion() {
        return TacticalPlanner.forTarget(test.game, test.nekro, target)
                .filter(plan -> plan.kind() == Kind.EXPAND && plan.landings().containsKey("wellon"));
    }

    // One PDS II next door fires a single die at the activated system. A cruiser beside the carrier takes the hit
    // first, so the landing still happens and the plan is charged the cruiser's expected loss instead of being vetoed.
    @Test
    void expandsIntoACoveredSystemWithEscortsAndPaysForTheExpectedLoss() {
        test.nekro.setSecret("mp");
        fleet(1);
        double open = expansion().orElseThrow().score();

        solCoversTheTarget(1);
        Optional<TacticalPlan> covered = expansion();

        assertThat(covered).isPresent();
        assertThat(covered.get().score()).isLessThan(open);
    }

    @Test
    void rejectsALoneCarrierIntoHeavyCover() {
        test.nekro.setSecret("mp");
        fleet(0);
        solCoversTheTarget(3);

        assertThat(expansion()).isEmpty();
    }

    @Test
    void chargesOnlyTheCheapestShipsAndKeepsTheCarrierAlive() {
        fleet(1);
        List<UnitMove> arriving = List.of(
                new UnitMove(home.getPosition(), "space", UnitType.Cruiser, 1),
                new UnitMove(home.getPosition(), "space", UnitType.Carrier, 1));
        Tile empty = test.place("20", AiTestGame.neighbourOf(target));

        CannonExposure.Outcome outcome = CannonExposure.of(test.nekro, empty, arriving, 1, List.of(0.5));

        assertThat(outcome.expectedLoss()).isCloseTo(0.5 * cost(UnitType.Cruiser), within(EXACT));
        assertThat(outcome.delivery()).isCloseTo(1.0, within(EXACT));
    }

    @Test
    void aSustainingShipAbsorbsTheFirstHitForTheCostOfItsDamage() {
        Tile empty = test.place("20", AiTestGame.neighbourOf(target));
        List<UnitMove> arriving = List.of(new UnitMove(home.getPosition(), "space", UnitType.Dreadnought, 1));

        CannonExposure.Outcome outcome = CannonExposure.of(test.nekro, empty, arriving, 0, List.of(0.6));

        assertThat(outcome.expectedLoss()).isCloseTo(0.6 * 0.25 * cost(UnitType.Dreadnought), within(EXACT));
        assertThat(outcome.delivery()).isCloseTo(1.0, within(EXACT));
    }

    @Test
    void aLoneShipThatOneHitKillsIsNotViableAtEvenOdds() {
        Tile empty = test.place("20", AiTestGame.neighbourOf(target));
        List<UnitMove> arriving = List.of(new UnitMove(home.getPosition(), "space", UnitType.Carrier, 1));

        CannonExposure.Outcome outcome = CannonExposure.of(test.nekro, empty, arriving, 1, List.of(0.6));

        assertThat(outcome.delivery()).isCloseTo(0.4, within(EXACT));
        assertThat(outcome.viable()).isFalse();
    }

    @Test
    void antimassDeflectorsLowerTheDiceTheCoverRolls() {
        solCoversTheTarget(2);
        Tile covered = test.game.getTileByPosition(target);
        double before = CannonExposure.expectedHits(test.game, test.nekro, covered);

        test.nekro.addTech("amd");
        double after = CannonExposure.expectedHits(test.game, test.nekro, covered);

        assertThat(before).isCloseTo(2 * 0.6, within(EXACT));
        assertThat(after).isCloseTo(2 * 0.5, within(EXACT));
    }

    private double cost(UnitType type) {
        return test.nekro.getUnitByType(type).getCost();
    }
}
