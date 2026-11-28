package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.secrets.SecretOdds.CardChance;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The audit found the leader threat ramped over a fixed distance from the goal (1.5 points, then 3 under the
// experiment), so several rivals looked like threats from 7/10 whatever they could actually score. With
// WIN_THREAT_FEASIBLE the threat is the rival's chance to reach the goal by the end of the coming status phase, built
// from the points it can feasibly get: one public per status phase (LRR 61.6, 81.1), Imperial's extra public and
// Mecatol point (LRR 45.2), secrets at their card-counting odds, the custodians and Styx when it can take them.
class StandingsSecretsTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;

    private DefenseScenario board;
    private Player sardakk;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        sardakk = board.sardakk;
        board.test.game.setVp(10);
    }

    private Standings standings() {
        return new DefenseOutlook(board.test.game, board.test.nekro).standings();
    }

    private void score(Player player, int points) {
        board.test.game.scorePublicObjective(player.getUserID(), board.test.game.addCustomPO("Test points", points));
    }

    private void disableForNekro() {
        AiExperiments.disable(board.test.nekro, Set.of(Experiment.WIN_THREAT_FEASIBLE));
    }

    // Nine of ten with nothing it can score this round: not a threat, however close it sits.
    @Test
    void aRivalOnePointOutWithNothingFeasibleIsNoThreat() {
        score(sardakk, 9);

        assertThat(standings().winThreat(sardakk)).isZero();
        assertThat(standings().denialWeight(sardakk))
                .isCloseTo(0.4 * standings().hurtWeight(sardakk), within(1e-9));
    }

    // The old ramp: nine of ten is a third of the way into the 1.5-point window.
    @Test
    void withTheExperimentDisabledTheThreatRampsOverTheOldWindow() {
        score(sardakk, 9);
        disableForNekro();

        assertThat(standings().winThreat(sardakk)).isCloseTo(1.0 / 3, within(1e-9));
    }

    // Seven of ten, but Sardakk holds the unplayed Imperial with Mecatol Rex (one point, LRR 45.2) and six command
    // tokens for Galvanize the People (two points): three sure points, so it is a certain threat.
    private void sardakkOnSevenWithThreeSurePoints() {
        score(sardakk, 7);
        board.reveal("galvanize");
        sardakk.setTacticalCC(3);
        sardakk.setStrategicCC(3);
        sardakk.addSC(IMPERIAL);
        sardakk.addPlanet("mr");
    }

    @Test
    void aRivalFurtherOutWithFeasiblePointsIsAThreat() {
        sardakkOnSevenWithThreeSurePoints();

        assertThat(standings().winThreat(sardakk)).isCloseTo(1.0, within(1e-9));
    }

    // The user's example: six of ten, but with three feasible sources worth four points. Imperial on Mecatol Rex (one
    // point, LRR 45.2) and Imperial's extra public, Galvanize the People (two points), in the action phase, then
    // Raise a Fleet (five non-fighter ships in one system, one point) in the status phase (LRR 61.6). A sure threat,
    // while a rival on eight of ten with nothing to score is none.
    @Test
    void aRivalOnSixWithFeasiblePointsIsAThreatAndOneOnEightWithoutIsNot() {
        score(sardakk, 6);
        board.reveal("galvanize");
        board.reveal("raise_fleet");
        sardakk.setTacticalCC(3);
        sardakk.setStrategicCC(3);
        sardakk.addSC(IMPERIAL);
        sardakk.addPlanet("mr");
        board.test.units(board.home, "space", sardakk, UnitType.Dreadnought, 5);

        assertThat(standings().winThreat(sardakk)).isCloseTo(1.0, within(1e-9));

        Player sol = board.test.sol;
        score(sol, 8);
        assertThat(standings().winThreat(sol)).isZero();
    }

    // Without Mecatol Rex the Imperial point is gone (no way onto it this round) and Galvanize alone leaves Sardakk
    // one short.
    @Test
    void imperialWithoutMecatolGivesNoPoint() {
        sardakkOnSevenWithThreeSurePoints();
        sardakk.removePlanet("mr");

        assertThat(standings().winThreat(sardakk)).isZero();
    }

    // Two unseen secrets on nine of ten: the threat is the exact chance that either card is ready, not a rounded
    // sure point.
    @Test
    void aRivalsSecretsCountAtTheirExactOdds() {
        score(sardakk, 9);
        sardakk.setSecret("dhw");
        sardakk.setSecret("lsc");

        double noneReady = 1;
        for (CardChance card : SecretOdds.readyByCard(board.test.game, board.test.nekro, sardakk)) {
            noneReady *= 1 - card.status() - card.action();
        }
        assertThat(standings().winThreat(sardakk)).isCloseTo(1 - noneReady, within(1e-9));
        assertThat(standings().winThreat(sardakk)).isBetween(0.0, 0.5);
    }

    @Test
    void aRivalsSecretCountsAtItsCardCountingOdds() {
        sardakk.setSecret("dhw");

        double odds = SecretOdds.chanceReady(board.test.game, board.test.nekro, sardakk);
        assertThat(standings().secretPoint(sardakk)).isCloseTo(odds, within(1e-9));
        assertThat(odds).isNotCloseTo(Standings.SECRET_CHANCE, within(1e-6));
    }

    @Test
    void withTheExperimentDisabledASecretIsAFlatChance() {
        sardakk.setSecret("dhw");
        disableForNekro();

        assertThat(standings().secretPoint(sardakk)).isCloseTo(Standings.SECRET_CHANCE, within(1e-9));
    }
}
