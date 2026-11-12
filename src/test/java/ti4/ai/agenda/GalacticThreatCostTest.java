package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class GalacticThreatCostTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";
    private static final String SHARD = "Law_Elect Player_7_shard_of_the_throne";

    private AiTestGame test;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":4,\"black\":0}");
    }

    // Nekro (the "black" seat) predicts that Xxcha is elected.
    private void nekroPredictsXxcha() {
        test.game.setCurrentAgendaVote("xxcha", "black_" + GalacticThreatCost.RIDER);
    }

    @Test
    void theNekroFactionHoldsGalacticThreat() {
        assertThat(test.nekro.hasAbility("galactic_threat")).isTrue();
    }

    @Test
    void thereIsNoCostWithoutAPrediction() {
        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 3)).isZero();
    }

    // The user's decided rule: the first voter for the outcome Nekro predicted pays 1 trade good of disincentive.
    @Test
    void theFirstVoteForThePredictedOutcomeCostsOneTradeGood() {
        nekroPredictsXxcha();

        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 3)).isEqualTo(1.0);
    }

    // The disincentive is flat: it does not depend on which technologies the voter holds.
    @Test
    void theCostDoesNotDependOnTheVotersTechnology() {
        nekroPredictsXxcha();
        test.sol.setTechs(new java.util.ArrayList<>());

        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 3)).isEqualTo(1.0);
    }

    @Test
    void votingAnotherOutcomeOrAbstainingCostsNothing() {
        nekroPredictsXxcha();

        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "sol", 3)).isZero();
        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 0)).isZero();
        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "", 0)).isZero();
    }

    // Once another (non-Nekro) player has voted for the prediction Nekro gets a technology anyway, so joining costs
    // half.
    @Test
    void joiningOtherVotersForThePredictionCostsHalf() {
        test.game.setCurrentAgendaVote("xxcha", "black_" + GalacticThreatCost.RIDER + ";red_4");

        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 3)).isEqualTo(0.5);
    }

    // Nekro's own votes on its prediction do not count as others having voted.
    @Test
    void nekrosOwnVotesDoNotHalveTheCost() {
        test.game.setCurrentAgendaVote("xxcha", "black_" + GalacticThreatCost.RIDER + ";black_2");

        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 3)).isEqualTo(1.0);
    }

    // The voter's own earlier votes are not "other players" having voted.
    @Test
    void ownEarlierVotesDoNotHalveTheCost() {
        test.game.setCurrentAgendaVote("xxcha", "black_" + GalacticThreatCost.RIDER + ";blue_2");

        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 3)).isEqualTo(1.0);
    }

    @Test
    void nekroPaysNothingForItsOwnPrediction() {
        nekroPredictsXxcha();

        assertThat(GalacticThreatCost.ofBallot(test.game, test.nekro, "xxcha", 3))
                .isZero();
    }

    @Test
    void theCostIsOffWithoutTheExperiment() {
        nekroPredictsXxcha();
        AiExperiments.disable(test.sol, Set.of(Experiment.GALACTIC_THREAT_COST));

        assertThat(GalacticThreatCost.ofBallot(test.game, test.sol, "xxcha", 3)).isZero();
    }

    // In Xxcha's forecast, Sol dislikes voting for the predicted outcome by the same cost.
    @Test
    void otherVotersDislikeThePredictedOutcomeInTheForecast() {
        nekroPredictsXxcha();
        double withCost = AgendaForecast.of(test.game, xxcha).utility(test.sol, "xxcha");
        AiExperiments.disable(xxcha, Set.of(Experiment.GALACTIC_THREAT_COST));
        double withoutCost = AgendaForecast.of(test.game, xxcha).utility(test.sol, "xxcha");

        assertThat(withoutCost - withCost).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void theForecastDislikeHalvesOnceOthersHaveVotedForThePrediction() {
        test.game.setCurrentAgendaVote("xxcha", "black_" + GalacticThreatCost.RIDER + ";red_4");
        double withCost = AgendaForecast.of(test.game, xxcha).utility(test.sol, "xxcha");
        AiExperiments.disable(xxcha, Set.of(Experiment.GALACTIC_THREAT_COST));
        double withoutCost = AgendaForecast.of(test.game, xxcha).utility(test.sol, "xxcha");

        assertThat(withoutCost - withCost).isCloseTo(0.5, within(1e-9));
    }

    // The viewing seat's own cost is charged on its ballot, not in its forecast, so it is not counted twice.
    @Test
    void theViewersOwnUtilityIsLeftToTheBallot() {
        nekroPredictsXxcha();
        double withCost = AgendaForecast.of(test.game, test.sol).utility(test.sol, "xxcha");
        AiExperiments.disable(test.sol, Set.of(Experiment.GALACTIC_THREAT_COST));
        double withoutCost = AgendaForecast.of(test.game, test.sol).utility(test.sol, "xxcha");

        assertThat(withCost).isCloseTo(withoutCost, within(1e-9));
    }

    // On Public Execution Sol wants a rival elected and Nekro and Xxcha are equal rivals, so the 1 trade good of
    // Galactic Threat cost is enough to move Sol's ballot off whichever rival Nekro predicted. Each rival holds three
    // action cards, so on the expert value table executing either one (losing its hand) is worth Sol's votes.
    @Test
    void theBallotMovesOffThePredictionWhenTheRivalsAreEqual() {
        test.game.setCurrentAgendaInfo("Directive_Elect Player_7_execution");
        int index = 1;
        for (Player rival : List.of(test.nekro, xxcha)) {
            for (String card : List.of("sabo1", "sabo2", "sabo3"))
                rival.setActionCard(card + rival.getColor(), index++);
        }
        String unpredicted = solsBallot().outcome();
        test.game.setCurrentAgendaVote(unpredicted, "black_" + GalacticThreatCost.RIDER);

        assertThat(solsBallot().outcome()).isNotEqualTo(unpredicted);

        AiExperiments.disable(test.sol, Set.of(Experiment.GALACTIC_THREAT_COST));
        assertThat(solsBallot().outcome()).isEqualTo(unpredicted);
    }

    // A big stake still beats the 1 trade good: Sol votes itself the Shard of the Throne even if Nekro predicted it.
    @Test
    void aBigStakeStillVotesForThePrediction() {
        test.game.setCurrentAgendaVote("sol", "black_" + GalacticThreatCost.RIDER);

        assertThat(solsBallot().outcome()).isEqualTo("sol");
    }

    private BallotPlanner.Choice solsBallot() {
        return BallotPlanner.choose(test.game, test.sol, AgendaPolicy.currentAgenda(test.game), List.of(3, 2, 4))
                .orElseThrow();
    }
}
