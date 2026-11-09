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
import ti4.ai.tactical.PlanetTake;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// WIN_THREAT_FEASIBLE: once the custodians token is gone, Mecatol Rex is taken by invasion (LRR 49: space combat,
// commit ground forces, space cannon defense, ground combat, establish control). A rival that can take it from its
// holder this round gains what control of Mecatol Rex scores: Imperial's Mecatol point (LRR 45.2) and any public
// objective the extra planet completes. Each counts only at the chance the rival wins both combats.
class MecatolTakeWinOddsTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final String ONE_AWAY = "101";
    private static final String TWO_AWAY = "201";
    private static final String EMPTY_SYSTEM = "46";
    private static final String MECATOL_PLANET = "mr";
    private static final int IMPERIAL = 8;
    private static final String[][] OUTPOSTS = {
        {"19", "306", "wellon"},
        {"20", "307", "vefutii"},
        {"21", "308", "thibah"},
        {"22", "309", "tarmann"},
        {"23", "310", "saudor"}
    };

    private AiTestGame test;
    private Player hacan;
    private Tile rex;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(4);
        test.aiIsActive("action");
        test.nekroHome();
        rex = test.place("18", MECATOL);
        test.place(EMPTY_SYSTEM, ONE_AWAY);
        test.place(EMPTY_SYSTEM, TWO_AWAY);
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        test.nekro.setTacticalCC(3);
        test.sol.setTacticalCC(3);
        test.sol.setFleetCC(8);
        hacan = test.addSeat("300000000000000003", "hacan", "yellow");
        hacan.addPlanet(MECATOL_PLANET);
    }

    private void weakHolder() {
        test.units(rex, MECATOL_PLANET, hacan, UnitType.Infantry, 1);
    }

    private void fortressHolder() {
        test.units(rex, "space", hacan, UnitType.Warsun, 2);
        test.units(rex, "space", hacan, UnitType.Dreadnought, 4);
        test.units(rex, MECATOL_PLANET, hacan, UnitType.Pds, 2);
        test.units(rex, MECATOL_PLANET, hacan, UnitType.Infantry, 6);
        test.units(rex, MECATOL_PLANET, hacan, UnitType.Mech, 1);
    }

    private void solFleetAt(String position) {
        Tile tile = test.game.getTileByPosition(position);
        test.units(tile, "space", test.sol, UnitType.Dreadnought, 2);
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);
        test.units(tile, "space", test.sol, UnitType.Infantry, 4);
    }

    private double take() {
        return PlanetTake.mecatolThisRound(test.game, test.nekro, test.sol);
    }

    // Nine of ten with the unplayed Imperial: taking Mecatol Rex from one infantry is the tenth point.
    @Test
    void aLeaderWhoseOnlyPathIsTakingMecatolFromAWeakHolderIsLikelyToFinish() {
        test.points(test.sol, 9);
        test.sol.addSC(IMPERIAL);
        weakHolder();
        solFleetAt(ONE_AWAY);

        double take = take();
        assertThat(take).isGreaterThan(0.8);
        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isCloseTo(take, within(1e-9));
        assertThat(WinOdds.of(test.game, test.nekro, test.sol).duringAction()).isCloseTo(take, within(1e-9));
    }

    @Test
    void aFortressHolderMakesTheSameTakeUnlikely() {
        test.points(test.sol, 9);
        test.sol.addSC(IMPERIAL);
        fortressHolder();
        solFleetAt(ONE_AWAY);

        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isLessThan(0.1);
    }

    // Carriers and dreadnoughts move 1 (no movement tech): from two systems away the take needs two activations.
    @Test
    void aFleetTwoMovesAwayCannotTakeItThisRound() {
        test.points(test.sol, 9);
        test.sol.addSC(IMPERIAL);
        weakHolder();
        solFleetAt(TWO_AWAY);

        assertThat(take()).isZero();
        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isZero();
    }

    // Without Imperial, control of Mecatol Rex alone scores nothing.
    @Test
    void takingMecatolWithoutImperialOrAnObjectiveScoresNothing() {
        test.points(test.sol, 9);
        weakHolder();
        solFleetAt(ONE_AWAY);

        assertThat(take()).isGreaterThan(0.8);
        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isZero();
    }

    // Expand Borders (control 6 planets outside home systems) with five outposts: one step short it is
    // SecretOdds.ONE_STEP_SHORT (0.5) ready; Mecatol Rex is the sixth planet, so a take makes it certain. Three
    // defending infantry keep the take short of certain so both branches show.
    @Test
    void mecatolCompletesAPlanetObjective() {
        test.points(test.sol, 9);
        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        for (String[] outpost : OUTPOSTS) {
            test.place(outpost[0], outpost[1]);
            test.sol.addPlanet(outpost[2]);
        }
        test.game.getRevealedPublicObjectives().put("expand_borders", 1);
        test.units(rex, MECATOL_PLANET, hacan, UnitType.Infantry, 3);
        solFleetAt(ONE_AWAY);

        double take = take();
        assertThat(take).isGreaterThan(0.0).isLessThan(1.0);
        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol))
                .isCloseTo(take + (1 - take) * 0.5, within(1e-9));
    }

    // Removing Sol's fleet beside Mecatol Rex breaks the only path, so WIN_BREAK_DENIAL values that removal.
    @Test
    void removingTheFleetInRangeDropsThePath() {
        test.points(test.sol, 9);
        test.sol.addSC(IMPERIAL);
        weakHolder();
        solFleetAt(ONE_AWAY);

        double before = WinOdds.reachesGoal(test.game, test.nekro, test.sol);
        assertThat(WinOdds.without(test.game, test.nekro, test.sol, Loss.ships(ONE_AWAY))
                        .byStatusEnd())
                .isZero();
        DenialMap map = DenialMap.build(test.game, test.nekro);
        assertThat(map.ships(test.sol, ONE_AWAY)).isCloseTo(before * ScoringStakes.WIN_VALUE, within(1e-6));
    }

    @Test
    void withTheExperimentDisabledTheTakeIsIgnored() {
        test.points(test.sol, 9);
        test.sol.addSC(IMPERIAL);
        weakHolder();
        solFleetAt(ONE_AWAY);
        AiExperiments.disable(test.nekro, Set.of(Experiment.WIN_THREAT_FEASIBLE));

        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isZero();
    }

    // CPU: one invasion estimate per rival per tick; the memo makes repeat calls free.
    @Test
    void theTakeEstimateIsCheap() {
        test.points(test.sol, 9);
        test.sol.addSC(IMPERIAL);
        fortressHolder();
        solFleetAt(ONE_AWAY);

        long start = System.nanoTime();
        int ticks = 20;
        for (int tick = 0; tick < ticks; tick++) {
            try (var ignored = DecisionMemo.open()) {
                for (int call = 0; call < 10; call++) WinOdds.reachesGoal(test.game, test.nekro, test.sol);
            }
        }
        double millisPerTick = (System.nanoTime() - start) / 1e6 / ticks;
        assertThat(millisPerTick).isLessThan(50);
    }
}
