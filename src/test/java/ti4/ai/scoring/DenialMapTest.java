package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.scoring.WinOdds.Loss;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// WIN_BREAK_DENIAL: for a rival who can feasibly win this round, take away one holding at a time and see how much its
// chance of winning before the seat drops. The drop times the stake (losing the game, ScoringStakes.WIN_VALUE) is what
// removing that holding is worth to the seat.
class DenialMapTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final String ONE_AWAY = "101";
    private static final String[][] OUTPOSTS = {
        {"19", "306", "wellon"},
        {"20", "307", "vefutii"},
        {"21", "308", "thibah"},
        {"22", "309", "tarmann"},
        {"23", "310", "saudor"},
        {"25", "311", "quann"}
    };
    private static final int IMPERIAL = 8;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(4);
        test.aiIsActive("action");
        test.nekroHome();
        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        test.nekro.setTacticalCC(3);
        test.sol.setTacticalCC(3);
        test.sol.setStrategicCC(0);
    }

    // Expand Borders: control 6 planets outside home systems (a stage I objective). Sol holds exactly six.
    private void solHoldsExpandBorders() {
        for (String[] outpost : OUTPOSTS) {
            test.place(outpost[0], outpost[1]);
            test.sol.addPlanet(outpost[2]);
        }
        test.game.getRevealedPublicObjectives().put("expand_borders", 1);
    }

    @Test
    void thePlanetThatCompletesTheOnlyPathIsWorthMostOfTheGame() {
        test.points(test.sol, 9);
        solHoldsExpandBorders();
        DenialMap map = DenialMap.build(test.game, test.nekro);

        assertThat(map.covers(test.sol)).isTrue();
        // Without Quann Sol is one planet short: the readiness model leaves it SecretOdds.ONE_STEP_SHORT (0.5) to
        // grab another before status, so half the win is broken.
        assertThat(map.planet(test.sol, "quann")).isCloseTo(0.5 * ScoringStakes.WIN_VALUE, within(1e-6));
        assertThat(map.planet(test.sol, "quann")).isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE);
        // Each outpost is worth the same; only the home planet (LRR 61.16) ranks above them.
        assertThat(map.removals(test.sol))
                .filteredOn(removal -> !removal.loss().planets().contains("jord"))
                .allSatisfy(
                        removal -> assertThat(removal.value()).isCloseTo(0.5 * ScoringStakes.WIN_VALUE, within(1e-6)));
        assertThat(map.removals(test.sol).getFirst().loss()).isEqualTo(Loss.planet("jord"));
    }

    // Galvanize the People (spend 6 command tokens, LRR 61) is a second path no planet feeds, so taking Quann does not
    // stop Sol from scoring one public objective at status (LRR 61.16 only blocks it when a home planet is lost).
    @Test
    void aRemovalThatLeavesAnotherPathIsWorthNothing() {
        test.points(test.sol, 9);
        solHoldsExpandBorders();
        test.game.getRevealedPublicObjectives().put("galvanize", 1);
        test.sol.setTacticalCC(3);
        test.sol.setStrategicCC(3);
        DenialMap map = DenialMap.build(test.game, test.nekro);

        assertThat(map.covers(test.sol)).isTrue();
        assertThat(map.planet(test.sol, "quann")).isCloseTo(0, within(1e-9));
    }

    // Losing a home planet stops all public scoring (LRR 61.16), which breaks both paths at once.
    @Test
    void takingAHomePlanetBlocksEveryPublicPath() {
        test.points(test.sol, 9);
        solHoldsExpandBorders();
        test.game.getRevealedPublicObjectives().put("galvanize", 1);
        test.sol.setStrategicCC(3);
        DenialMap map = DenialMap.build(test.game, test.nekro);

        assertThat(map.planet(test.sol, "jord")).isCloseTo(ScoringStakes.WIN_VALUE, within(1e-6));
    }

    @Test
    void aRivalWhoCannotWinThisRoundGetsNoMap() {
        test.points(test.sol, 3);
        solHoldsExpandBorders();
        DenialMap map = DenialMap.build(test.game, test.nekro);

        assertThat(map.isEmpty()).isTrue();
        assertThat(map.planet(test.sol, "quann")).isZero();
    }

    @Test
    void withTheExperimentOffTheMapIsEmpty() {
        test.points(test.sol, 9);
        solHoldsExpandBorders();
        AiExperiments.disable(test.nekro, Set.of(Experiment.WIN_BREAK_DENIAL));
        DenialMap map = DenialMap.of(test.game, test.nekro);

        assertThat(map.isEmpty()).isTrue();
        assertThat(map.planet(test.sol, "quann")).isZero();
        assertThat(map.locked(test.sol, ONE_AWAY)).isZero();
    }

    @Test
    void noLossLeavesTheOddsUnchanged() {
        test.points(test.sol, 9);
        solHoldsExpandBorders();

        assertThat(WinOdds.without(test.game, test.nekro, test.sol, Loss.NONE))
                .isEqualTo(WinOdds.of(test.game, test.nekro, test.sol));
    }

    // Ships in a system holding their own command token cannot move (LRR 58.4c): Signal Jamming the staging system
    // stops the only custodians landing (LRR 55) that would take Sol to ten.
    @Test
    void lockingTheStagingSystemBreaksACustodiansWin() {
        test.points(test.sol, 9);
        Tile rex = test.place("18", MECATOL);
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        Tile staging = test.place("46", ONE_AWAY);
        test.units(staging, "space", test.sol, UnitType.Carrier, 1);
        test.units(staging, "space", test.sol, UnitType.Infantry, 1);
        test.sol.setTg(6);
        DenialMap map = DenialMap.build(test.game, test.nekro);

        assertThat(map.covers(test.sol)).isTrue();
        assertThat(map.locked(test.sol, ONE_AWAY)).isCloseTo(ScoringStakes.WIN_VALUE, within(1e-6));
        assertThat(map.ships(test.sol, ONE_AWAY)).isCloseTo(ScoringStakes.WIN_VALUE, within(1e-6));
    }

    @Test
    void timesTheMap() {
        test.points(test.sol, 9);
        solHoldsExpandBorders();
        Tile rex = test.place("18", MECATOL);
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        Tile staging = test.place("46", ONE_AWAY);
        test.units(staging, "space", test.sol, UnitType.Carrier, 1);
        test.units(staging, "space", test.sol, UnitType.Infantry, 1);
        test.sol.setTg(6);
        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            DenialMap.build(test.game, test.nekro).removals(test.sol);
        }
        int runs = 20;
        long start = System.nanoTime();
        int removals = 0;
        for (int run = 0; run < runs; run++) {
            try (DecisionMemo.Open ignored = DecisionMemo.open()) {
                removals = DenialMap.build(test.game, test.nekro)
                        .removals(test.sol)
                        .size();
            }
        }
        double millis = (System.nanoTime() - start) / 1e6 / runs;
        System.out.printf("DenialMap: %d removals for one contender in %.2f ms%n", removals, millis);
        assertThat(removals).isEqualTo(OUTPOSTS.length + 2);
        assertThat(millis).isLessThan(250);
    }
}
