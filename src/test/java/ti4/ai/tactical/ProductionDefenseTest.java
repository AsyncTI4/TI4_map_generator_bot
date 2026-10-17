package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// What the production planner buys to make an attack on home unappetizing, and when it buys nothing.
class ProductionDefenseTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";
    private static final double FAIR_FIGHT = 0.55;
    private static final double LONG_SHOT = 0.80;

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        board.test.nekro.setTg(6);
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(board.test.game, board.test.nekro, board.home);
    }

    private static int defensiveGroundUnits(BuildPlan plan) {
        return plan.orders().stream()
                .filter(BuildOrder::defense)
                .filter(order -> BoardView.GROUND_FORCES.contains(order.type()))
                .mapToInt(BuildOrder::units)
                .sum();
    }

    private double takeAfter(BuildPlan plan) {
        DefenseOutlook outlook = new DefenseOutlook(board.test.game, board.test.nekro);
        return outlook.chances(board.sardakk, board.home, Horizon.IMMEDIATE, board.afterBuilding(plan))
                .take();
    }

    private void roundFourThreat(boolean withConquer) {
        board.raidingFleet(1, 2, 4);
        if (withConquer) board.reveal("conquer");
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 1);
    }

    // Test 3: Sardakk's landing party can win Mordai II and Conquer the Weak makes it worth the action, so the dock
    // spends its production on infantry until the landing is no better than a fair fight.
    @Test
    void buildsInfantryUntilTheLandingIsAFairFight() {
        roundFourThreat(true);

        BuildPlan plan = plan();

        assertThat(defensiveGroundUnits(plan)).isGreaterThanOrEqualTo(2);
        assertThat(takeAfter(plan)).isLessThanOrEqualTo(FAIR_FIGHT);
    }

    // Test 3b: with nothing to score for, a Sardakk landing is less likely, so it is enough to hold it to a long shot.
    @Test
    void buildsLessWhenTheNeighbourHasNoReasonToScore() {
        roundFourThreat(true);
        int withReason = defensiveGroundUnits(plan());
        DefenseScenario calm = new DefenseScenario();
        calm.test.nekro.setTg(6);
        calm.raidingFleet(1, 2, 4);
        calm.test.units(calm.home, HOME_PLANET, calm.test.nekro, UnitType.Infantry, 1);
        BuildPlan calmPlan = ProductionPlanner.plan(calm.test.game, calm.test.nekro, calm.home);

        assertThat(defensiveGroundUnits(calmPlan)).isLessThan(withReason);
        DefenseOutlook outlook = new DefenseOutlook(calm.test.game, calm.test.nekro);
        assertThat(outlook.chances(calm.sardakk, calm.home, Horizon.IMMEDIATE, calm.afterBuilding(calmPlan))
                        .take())
                .isLessThanOrEqualTo(LONG_SHOT);
    }

    // Test 4: Letnev's home has two planets, and Sardakk can land on the one with no infantry whatever is built on the
    // dock planet. Ground forces there would be wasted; ships and fighters in orbit are what stop a landing.
    @Test
    void defendsATwoPlanetHomeInSpaceNotOnTheGround() {
        board.test.place("10", AiTestGame.HOME);
        board.test.nekro.setHomeSystemPosition(AiTestGame.HOME);
        board.test.nekro.removePlanet(HOME_PLANET);
        board.test.nekro.addPlanet("arcprime");
        board.test.nekro.addPlanet("wrenterra");
        Tile letnevHome = board.test.game.getTileByPosition(AiTestGame.HOME);
        board.test.units(letnevHome, "arcprime", board.test.nekro, UnitType.Spacedock, 1);
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");

        BuildPlan plan = ProductionPlanner.plan(board.test.game, board.test.nekro, letnevHome);

        assertThat(plan.orders().stream()
                        .filter(BuildOrder::defense)
                        .filter(order -> !BoardView.GROUND_FORCES.contains(order.type())))
                .isNotEmpty();
        assertThat(defensiveGroundUnits(plan)).isZero();
    }

    // A home no enemy ship can reach buys no defence at all.
    @Test
    void buysNoDefenceWhenNothingCanReachHome() {
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 1);

        assertThat(plan().defense()).isFalse();
    }

    // Test 9: war sun, four dreadnoughts and six infantry next door beat any garrison the dock could build in one
    // turn, and spending on it is a waste: the planner buys little or nothing for defence.
    @Test
    void doesNotThrowResourcesAtAnOverwhelmingFleet() {
        board.sardakk.addOwnedUnitByID("warsun");
        board.test.units(board.staging, "space", board.sardakk, UnitType.Warsun, 1);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 4);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Infantry, 6);
        board.reveal("conquer");
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 1);

        BuildPlan plan = plan();

        assertThat(defensiveGroundUnits(plan)).isLessThanOrEqualTo(1);
    }

    // Test 9, second half: against the same fleet infantry above the garrison floor still go out to take a planet,
    // since
    // keeping them would not change the outcome.
    @Test
    void sparesInfantryForExpansionAgainstAnOverwhelmingFleet() {
        board.sardakk.addOwnedUnitByID("warsun");
        board.test.units(board.staging, "space", board.sardakk, UnitType.Warsun, 1);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 4);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Infantry, 6);
        board.reveal("conquer");
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 5);
        String position = board.otherNeighbourOfHome();
        board.test.place("26", position);

        TacticalPlan plan = TacticalPlanner.forTarget(board.test.game, board.test.nekro, position)
                .filter(found -> found.kind() == Kind.EXPAND)
                .orElseThrow();

        int infantry = plan.moves().stream()
                .filter(move -> move.type() == UnitType.Infantry)
                .mapToInt(TacticalPlan.UnitMove::count)
                .sum();
        assertThat(infantry).isGreaterThanOrEqualTo(3);
    }

    // Test 15: working out what to build never looks at the neighbour's hidden hand.
    @Test
    void buildingNeverLooksAtAnEnemysHiddenCards() {
        roundFourThreat(true);
        board.sardakk.setSecret("sar");
        board.sardakk.setActionCard("sabo1");
        board.sardakk.setPromissoryNote("black_ta", 17);
        Player spy = Mockito.spy(board.sardakk);
        board.test.game.getPlayers().put(spy.getUserID(), spy);

        plan();

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }
}
