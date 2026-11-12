package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgendaForecastTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";
    private static final String SHARD = "Law_Elect Player_7_shard_of_the_throne";
    private static final String ARMS_REDUCTION = "Directive_For/Against_7_arms_reduction";

    private AiTestGame test;
    private Player xxcha;
    private int awards;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setSpeakerUserID(test.sol.getUserID());
        votes(9, 4, 0);
    }

    private void votes(int sol, int xxcha, int nekro) {
        test.game.setStoredValue(
                "agendaStartVoteCounts", "{\"blue\":" + sol + ",\"red\":" + xxcha + ",\"black\":" + nekro + "}");
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(player.getUserID(), test.game.addCustomPO("Test points " + ++awards, points));
    }

    // The legacy forecast, where a voter with no view on the outcome votes at random 70% of the time.
    private void randomIndifferentVoters() {
        AiExperiments.disable(test.nekro, Set.of(Experiment.AGENDA_VALUE_TABLE));
    }

    private AgendaForecast forecast() {
        return AgendaForecast.of(test.game, test.nekro);
    }

    // The biggest bloc of votes decides when the voters back different outcomes: each of Sol and Xxcha is elected by
    // its own votes, and Sol has more than twice Xxcha's.
    @Test
    void aBigBlocDecides() {
        AgendaForecast forecast = forecast();

        assertThat(forecast.chance("sol")).isGreaterThan(0.7);
        assertThat(forecast.chance("sol")).isGreaterThan(forecast.chance("xxcha"));
        assertThat(forecast.mostLikely()).contains("sol");
    }

    // With the votes level and each side voting for itself, the tie goes to the speaker's own choice.
    @Test
    void aTieGoesToTheSpeaker() {
        votes(5, 5, 0);
        AgendaForecast solSpeaks = forecast();
        test.game.setSpeakerUserID(xxcha.getUserID());
        AgendaForecast xxchaSpeaks = forecast();

        assertThat(solSpeaks.chance("sol")).isGreaterThan(solSpeaks.chance("xxcha"));
        assertThat(xxchaSpeaks.chance("xxcha")).isGreaterThan(xxchaSpeaks.chance("sol"));
    }

    @Test
    void theChancesAddUpToOne() {
        assertThat(forecast().chances().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum())
                .isCloseTo(1.0, within(1e-9));
    }

    // A rider means its owner does not vote, so Sol's 9 votes stop deciding the election.
    @Test
    void aRiderTakesAwayItsOwnersVotes() {
        AgendaForecast forecast = forecast();
        AgendaForecast withRider = forecast.withRider(test.sol, "Technology Rider", "xxcha");

        assertThat(withRider.chance("sol")).isLessThan(forecast.chance("sol"));
        assertThat(withRider.chance("xxcha")).isGreaterThan(forecast.chance("xxcha"));
    }

    @Test
    void aRiderPlacedInTheGameCountsLikeAHypotheticalOne() {
        AgendaForecast hypothetical = forecast().withRider(test.sol, "Technology Rider", "xxcha");
        test.game.setCurrentAgendaVote("xxcha", "blue_Technology Rider");

        assertThat(forecast().chances()).isEqualTo(hypothetical.chances());
    }

    @Test
    void theXxchaCommanderLetsARiderOwnerKeepItsVotes() {
        xxcha.addLeader("xxchacommander");
        xxcha.unsafeGetLeader("xxchacommander").setLocked(false);
        AgendaForecast forecast = forecast();

        assertThat(forecast.withRider(xxcha, "Technology Rider", "sol").chances())
                .isEqualTo(forecast.chances());
    }

    @Test
    void aSeatInTheAssassinatedRepresentativesHasNoVotes() {
        test.game.setStoredValue("AssassinatedReps", "sol");

        assertThat(forecast().chance("sol")).isLessThan(0.3);
    }

    // Votes already cast are final, and the voters who have voted are done.
    @Test
    void castVotesAreCountedAndTheVotersWhoVotedAreDone() {
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setActivePlayerID(test.sol.getUserID());
        test.game.setCurrentAgendaVote("xxcha", "red_4");

        AgendaForecast forecast = forecast();

        assertThat(forecast.chance("xxcha")).isLessThan(forecast.chance("sol"));

        test.game.setActivePlayerID(test.nekro.getUserID());
        test.game.setCurrentAgendaVote("xxcha", "red_10");
        assertThat(forecast().chance("xxcha")).isEqualTo(1.0);
    }

    // The same game and agenda always give the same numbers: the draws are seeded.
    @Test
    void theSameGameGivesTheSameForecast() {
        assertThat(forecast().chances()).isEqualTo(forecast().chances());
    }

    @Test
    void aHypotheticalThatChangesNothingChangesNothing() {
        AgendaForecast forecast = forecast();

        assertThat(forecast.withRider(test.nekro, "Politics Rider", "sol").chances())
                .isEqualTo(forecast.chances());
        assertThat(forecast.without(test.nekro).chances()).isEqualTo(forecast.chances());
    }

    @Test
    void silencingAVoterMovesTheElection() {
        AgendaForecast forecast = forecast();

        assertThat(forecast.without(test.sol).chance("xxcha")).isGreaterThan(forecast.chance("xxcha"));
    }

    // Seeing a rival's Imperial Rider on "for", voters who had no view on the directive turn against it, and the more
    // so the nearer the rival is to winning.
    @Test
    void aRivalsImperialRiderMovesTheVotersWhoHadNoPreference() {
        randomIndifferentVoters();
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(3, 3, 0);
        points(xxcha, 9);
        AgendaForecast forecast = forecast();

        AgendaForecast withRider = forecast.withRider(xxcha, RiderPayoff.IMPERIAL, "for");

        assertThat(forecast.chance("for")).isCloseTo(0.5, within(0.2));
        assertThat(withRider.chance("for")).isLessThan(0.1);
    }

    // A point is a big deal for anyone in the race, but a rider owner far behind the leader is no threat yet.
    @Test
    void anImperialRiderOfASeatFarBehindBarelyMovesTheVoters() {
        randomIndifferentVoters();
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(3, 3, 0);
        points(test.sol, 4);
        AgendaForecast forecast = forecast();

        AgendaForecast withRider = forecast.withRider(xxcha, RiderPayoff.IMPERIAL, "for");

        assertThat(withRider.chance("for")).isCloseTo(forecast.chance("for"), within(0.3));
    }

    // On the expert table a voter with no view on the outcome abstains instead of voting at random, so a table that
    // does not care leaves the outcome to the speaker's tie-break (milestone 4b, first step).
    @Test
    void anIndifferentTableLeavesTheOutcomeToTheSpeaker() {
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(3, 3, 0);

        assertThat(forecast().chances().values()).contains(1.0);

        randomIndifferentVoters();
        assertThat(forecast().chance("for")).isCloseTo(0.5, within(0.2));
    }

    // On a first agenda each seat keeps its smallest planet's votes back for the second (milestone 4c): Sol, with one
    // planet (Jord, 2 influence), has nothing left to cast; on the last agenda it casts both votes.
    @Test
    void votersKeepTheirSmallestPlanetBackOnTheFirstAgenda() {
        test.game.setStoredValue("agendaCount", "1");
        test.place("01", "304");
        test.sol.addPlanet("jord");
        votes(2, 2, 0);

        assertThat(forecast().votesLeft(test.sol)).isZero();

        test.game.setStoredValue("agendaCount", "2");
        assertThat(forecast().votesLeft(test.sol)).isEqualTo(2);
    }

    // Under Representative Government each seat casts 1 vote and exhausts no planet for it, so nothing is kept back for
    // the second agenda: the agenda-start snapshot of 1 vote stays castable.
    @Test
    void representativeGovernmentKeepsNoVoteBack() {
        test.game.setStoredValue("agendaCount", "1");
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.game.addLaw("rep_govt", null);
        votes(1, 1, 0);

        assertThat(forecast().votesLeft(test.sol)).isEqualTo(1);

        test.game.setStoredValue("executiveOrder", "sol");
        assertThat(forecast().votesLeft(test.sol)).isZero();
    }

    // Galactic Threat pays only through winning voters: with no votes left at the table the speaker picks the outcome
    // and Nekro's prediction pays nothing, so Nekro's expected, per-outcome and tie-break utilities all leave the
    // payoff
    // out (a Nekro speaker does not break a tie toward a prediction nobody voted for).
    @Test
    void aGalacticThreatOnAnOutcomeWithoutVotersIsWorthNothing() {
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(0, 0, 0);
        AgendaForecast forecast = forecast();
        String picked = forecast.mostLikely().orElseThrow();
        assertThat(forecast.chance(picked)).isEqualTo(1.0);
        assertThat(forecast.votedChance(picked)).isZero();

        AgendaForecast predicted = forecast.withRider(test.nekro, GalacticThreatCost.RIDER, picked);

        assertThat(predicted.utility(test.nekro, picked)).isCloseTo(forecast.utility(test.nekro, picked), within(1e-9));
        assertThat(predicted.tieBreakUtility(test.nekro, picked))
                .isCloseTo(forecast.tieBreakUtility(test.nekro, picked), within(1e-9));
        assertThat(predicted.votedChance(picked)).isZero();
        assertThat(predicted.expectedUtility(test.nekro))
                .isCloseTo(predicted.expected(outcome -> forecast.utility(test.nekro, outcome)), within(1e-9));
    }

    // Nekro's own votes (with the Xxcha commander's alliance) give it no technology: an outcome only Nekro voted for
    // pays its prediction nothing.
    @Test
    void nekrosOwnVotesDoNotPayItsGalacticThreat() {
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(0, 0, 0);
        AgendaForecast predicted = forecast()
                .withRider(test.nekro, GalacticThreatCost.RIDER, "for")
                .withVotes(test.nekro, "for", 5);

        assertThat(predicted.chance("for")).isEqualTo(1.0);
        assertThat(predicted.votedChance("for")).isZero();
        assertThat(predicted.withVotes(test.sol, "for", 1).votedChance("for")).isEqualTo(1.0);
    }

    // Galactic Threat copies a technology from a player who voted the predicted way, so the prediction is worth the
    // best technology of the voters on that outcome, not the best one at the table: with only Sol (Antimass
    // Deflectors) voting for, Xxcha's Gravity Drive is out of reach and Nekro counts Antimass Deflectors.
    @Test
    void aGalacticThreatIsWorthTheTechnologyOfTheVotersOnThatOutcome() {
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(0, 0, 0);
        test.nekro.setTechs(new java.util.ArrayList<>());
        test.sol.setTechs(new java.util.ArrayList<>(java.util.List.of("amd")));
        xxcha.setTechs(new java.util.ArrayList<>(java.util.List.of("gd")));
        AgendaForecast base = forecast();
        AgendaForecast predicted = base.withRider(test.nekro, GalacticThreatCost.RIDER, "for");
        assertThat(RiderPayoff.galacticThreat(test.game, test.nekro, test.nekro))
                .isCloseTo(ti4.ai.strategy.CopiedTechPolicy.copiedWorth("gd"), within(1e-9));

        AgendaForecast solVotes = predicted.withVotes(test.sol, "for", 1);
        double amd = ti4.ai.strategy.CopiedTechPolicy.copiedWorth("amd");
        assertThat(solVotes.threatWorth("for")).isCloseTo(amd, within(1e-9));
        assertThat(solVotes.utility(test.nekro, "for") - base.utility(test.nekro, "for"))
                .isCloseTo(amd, within(1e-9));
        assertThat(solVotes.expectedUtility(test.nekro))
                .isCloseTo(solVotes.expected(outcome -> base.utility(test.nekro, outcome)) + amd, within(1e-9));

        AgendaForecast bothVote = solVotes.withVotes(xxcha, "for", 1);
        assertThat(bothVote.threatWorth("for"))
                .isCloseTo(ti4.ai.strategy.CopiedTechPolicy.copiedWorth("gd"), within(1e-9));
    }

    // With voters on the predicted outcome the prediction pays, and Nekro's expected utility counts it.
    @Test
    void aGalacticThreatOnAnOutcomeWithVotersCountsItsPayoff() {
        AgendaForecast forecast = forecast();
        AgendaForecast predicted = forecast.withRider(test.nekro, GalacticThreatCost.RIDER, "sol");

        assertThat(forecast.votedChance("sol")).isPositive();
        assertThat(predicted.expectedUtility(test.nekro)).isGreaterThan(forecast.expectedUtility(test.nekro));
    }

    @Test
    void isEmptyInHiddenAgendaMode() {
        test.game.setHiddenAgendaMode(true);

        assertThat(forecast().isEmpty()).isTrue();
        assertThat(forecast().chances()).isEqualTo(Map.of());
    }

    @Test
    void isEmptyOutsideTheAgendaPhaseAndForAnAgendaItCannotEnumerate() {
        test.game.setCurrentAgendaInfo("Directive_Elect Something Else_7_not_an_agenda");
        assertThat(forecast().isEmpty()).isTrue();

        test.game.setCurrentAgendaInfo(SHARD);
        test.game.setPhaseOfGame("action");
        assertThat(forecast().isEmpty()).isTrue();
    }

    @Test
    void reportsTheOutcomeAPlacedRiderSitsOn() {
        test.game.setCurrentAgendaVote("sol", "red_Imperial Rider");

        assertThat(forecast().riderOutcome(xxcha, RiderPayoff.IMPERIAL)).contains("sol");
        assertThat(forecast().riderOutcome(test.sol, RiderPayoff.IMPERIAL)).isEmpty();
    }
}
