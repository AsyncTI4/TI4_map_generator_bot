package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.secrets.SecretOdds;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// WIN_THREAT_FEASIBLE: a rival's chance to reach the goal by the end of the coming status phase, from what it can
// feasibly score or take. Here the custodians (1 point for landing on Mecatol Rex, paying 6 influence, LRR 55) and
// Imperial's Mecatol point (LRR 45.2), which both come from the same landing, and secrets that never round up.
class WinOddsTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final String ONE_AWAY = "101";
    private static final String EMPTY_SYSTEM = "46";
    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private int awards;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(4);
        test.aiIsActive("action");
        test.nekroHome();
        Tile rex = test.place("18", MECATOL);
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        test.place(EMPTY_SYSTEM, ONE_AWAY);
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        test.nekro.setTacticalCC(3);
        test.sol.setTacticalCC(3);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(player.getUserID(), test.game.addCustomPO("Test points " + (++awards), points));
    }

    private void solCanLandOnMecatol() {
        Tile tile = test.game.getTileByPosition(ONE_AWAY);
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);
        test.units(tile, "space", test.sol, UnitType.Infantry, 1);
        test.sol.setTg(6);
    }

    @Test
    void aRivalThatCanLandOnTheCustodiansThisRoundCanFinish() {
        points(test.sol, 9);
        solCanLandOnMecatol();

        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isCloseTo(1.0, within(1e-9));
        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
    }

    @Test
    void withTheExperimentDisabledTheCustodiansAreIgnoredByTheRace() {
        points(test.sol, 9);
        solCanLandOnMecatol();
        AiExperiments.disable(test.nekro, Set.of(Experiment.WIN_THREAT_FEASIBLE));

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
    }

    // Without the influence the landing scores nothing.
    @Test
    void aLandingItCannotPayForIsNoPoint() {
        points(test.sol, 9);
        solCanLandOnMecatol();
        test.sol.setTg(0);

        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isZero();
    }

    // Eight of ten with the unplayed Imperial: landing on Mecatol Rex is the custodians point and then Imperial's
    // Mecatol point, two points from one source.
    @Test
    void imperialAndTheCustodiansLandingAreTwoPointsTogether() {
        points(test.sol, 8);
        solCanLandOnMecatol();
        test.sol.addSC(IMPERIAL);

        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void imperialWithoutAWayOntoMecatolIsNoPoint() {
        points(test.sol, 9);
        test.sol.addSC(IMPERIAL);

        assertThat(WinOdds.reachesGoal(test.game, test.nekro, test.sol)).isZero();
        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
    }

    // One unseen secret on nine of ten: a real chance, but well short of a sure point, so Sol is not taken to win
    // first.
    @Test
    void aSingleUnseenSecretIsAChanceNotAPoint() {
        points(test.sol, 9);
        test.sol.setSecret("dhw");

        double odds = WinOdds.reachesGoal(test.game, test.nekro, test.sol);
        assertThat(odds).isGreaterThan(0.0).isLessThan(WinOdds.MAJORITY);
        assertThat(odds).isLessThanOrEqualTo(SecretOdds.chanceReady(test.game, test.nekro, test.sol) + 1e-9);
        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
    }
}
