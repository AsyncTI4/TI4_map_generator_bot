package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// How the planner trades the home system's safety against what its fleet could do elsewhere: it holds back only what
// the
// defence model asks for, and brings ships home when home is exposed.
class TacticalDefenseTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";

    private DefenseScenario board;
    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        test = board.test;
        home = board.home;
    }

    private Optional<TacticalPlan> planFor(String position, Kind kind) {
        return TacticalPlanner.forTarget(test.game, test.nekro, position).filter(plan -> plan.kind() == kind);
    }

    private static int moved(TacticalPlan plan, UnitType type) {
        return plan.moves().stream()
                .filter(move -> move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
    }

    // Sol's lone destroyer on the system beside home: a space-only target worth a full point to a seat holding Spark a
    // Rebellion while everyone is level on points.
    private String lureForSparkARebellion() {
        String position = board.otherNeighbourOfHome();
        Tile target = test.place("47", position);
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
        test.nekro.setSecret("sar");
        return position;
    }

    // Test 1: no enemy ship can reach home, so nothing is held back for it and the attack takes everything that
    // reaches.
    @Test
    void anAttackTakesEveryShipThatReachesWhenNothingThreatensHome() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        String position = board.otherNeighbourOfHome();
        Tile lodor = test.place("26", position);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 2);

        TacticalPlan plan = planFor(position, Kind.ATTACK).orElseThrow();

        assertThat(new DefenseOutlook(test.game, test.nekro).cost(home)).isZero();
        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Dreadnought)).isEqualTo(2);
    }

    // Test 8: a round-four Sardakk fleet with a reason to take Mordai II sits next door. Of two dreadnoughts and a
    // pair of fighters, the attack elsewhere may take only what leaves home within the tolerance: one dreadnought
    // stays.
    @Test
    void anAggressiveNeighbourKeepsExactlyOneDreadnoughtHome() {
        board.raidingFleet(1, 1, 4);
        board.reveal("conquer");
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        String lure = lureForSparkARebellion();

        TacticalPlan plan = planFor(lure, Kind.ATTACK).orElseThrow();

        DefenseOutlook outlook = new DefenseOutlook(test.game, test.nekro);
        Defenders before = Defenders.of(test.nekro, home);
        Defenders after = before.minus(plan.moves());
        assertThat(moved(plan, UnitType.Dreadnought)).isEqualTo(1);
        assertThat(outlook.cost(home, after) - outlook.cost(home, before))
                .isLessThanOrEqualTo(DefenseOutlook.GARRISON_TOLERANCE);
    }

    // Test 8, second half: early in the game, against a passive faction and with nothing public to gain, the same fleet
    // has nothing to hold back for and everything that reaches goes.
    @Test
    void aPassiveEarlyNeighbourKeepsNothingHome() {
        test.game.setRound(1);
        Player hacan = test.addSeat("200000000000000003", "hacan", "green");
        test.units(board.staging, "space", hacan, UnitType.Carrier, 1);
        test.units(board.staging, "space", hacan, UnitType.Cruiser, 1);
        test.units(board.staging, "space", hacan, UnitType.Infantry, 4);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        String lure = lureForSparkARebellion();

        TacticalPlan plan = planFor(lure, Kind.ATTACK).orElseThrow();

        assertThat(moved(plan, UnitType.Dreadnought)).isEqualTo(2);
    }

    // Test 12: home is exposed (no ship, one infantry, a round-four fleet and a reason to land), nothing can be built,
    // and a dreadnought one system away is the only thing that can help: the best plan is to bring it home.
    @Test
    void bringsADreadnoughtHomeWhenHomeIsExposedAndNothingCanBeBuilt() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        String awayPosition = board.otherNeighbourOfHome();
        Tile away = test.place("47", awayPosition);
        test.units(away, "space", test.nekro, UnitType.Dreadnought, 1);
        test.nekro.exhaustPlanet(HOME_PLANET);
        test.nekro.setTg(0);

        Optional<TacticalPlan> best = TacticalPlanner.best(test.game, test.nekro);

        assertThat(best).isPresent();
        assertThat(best.get().kind()).isEqualTo(Kind.DEFEND);
        assertThat(best.get().target()).isEqualTo(AiTestGame.HOME);
        assertThat(best.get().moves()).containsExactly(new UnitMove(awayPosition, "space", UnitType.Dreadnought, 1));
    }

    // Test 14: retaking a planet in the seat's own home system is worth the public scoring lock on top of the planet,
    // so
    // the same invasion scores that much higher against Mordai II than against a planet of about the same size
    // elsewhere.
    @Test
    void retakingAPlanetInItsOwnHomeIsWorthTheScoringLock() {
        double home = retakeScore(true);
        double elsewhere = retakeScore(false);

        assertThat(home - elsewhere)
                .isGreaterThan(0.85 * DefenseOutlook.PUBLIC_SCORING_LOCK)
                .isLessThan(1.1 * DefenseOutlook.PUBLIC_SCORING_LOCK + 1);
    }

    private double retakeScore(boolean inOwnHome) {
        DefenseScenario fresh = new DefenseScenario();
        AiTestGame game = fresh.test;
        game.units(fresh.staging, "space", game.nekro, UnitType.Carrier, 1);
        game.units(fresh.staging, "space", game.nekro, UnitType.Infantry, 4);
        game.nekro.setFleetCC(5);
        String target;
        if (inOwnHome) {
            game.nekro.removePlanet(HOME_PLANET);
            fresh.home.removeUnit(HOME_PLANET, Units.getUnitKey(UnitType.Spacedock, game.nekro.getColor()), 1);
            game.sol.addPlanet(HOME_PLANET);
            game.units(fresh.home, HOME_PLANET, game.sol, UnitType.Infantry, 1);
            target = AiTestGame.HOME;
        } else {
            target = fresh.beyondStaging();
            Tile lodor = game.place("26", target);
            game.sol.addPlanet("lodor");
            game.units(lodor, "lodor", game.sol, UnitType.Infantry, 1);
        }
        return TacticalPlanner.forTarget(game.game, game.nekro, target)
                .filter(plan -> plan.kind() == Kind.ATTACK)
                .orElseThrow()
                .score();
    }

    // Test 21: a single-planet home with a carrier and six infantry under the round-four threat. The carrier still
    // expands, but the garrison stays: the two-pass check notices that once the carrier is gone more infantry are
    // needed at home, and takes only the spare ones.
    @Test
    void expandsWithTheCarrierButLeavesTheGarrisonThatTheThreatNeeds() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        String position = board.otherNeighbourOfHome();
        test.place("26", position);

        TacticalPlan plan = planFor(position, Kind.EXPAND).orElseThrow();

        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Infantry)).isLessThanOrEqualTo(2);
        assertThat(plan.landings().get("lodor")).isGreaterThanOrEqualTo(1);
    }

    // Test 15: weighing the neighbour only ever uses what the table can see of it.
    @Test
    void rankingPlansNeverLooksAtAnEnemysHiddenCards() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        lureForSparkARebellion();
        board.sardakk.setSecret("sar");
        board.sardakk.setActionCard("sabo1");
        board.sardakk.setPromissoryNote("black_ta", 17);
        Player spy = Mockito.spy(board.sardakk);
        test.game.getPlayers().put(spy.getUserID(), spy);

        TacticalPlanner.ranked(test.game, test.nekro);
        TacticalPlanner.best(test.game, test.nekro);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }
}
