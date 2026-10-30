package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(3, 3, 0);
        points(xxcha, 9);
        AgendaForecast forecast = forecast();

        AgendaForecast withRider = forecast.withRider(xxcha, RiderPayoff.IMPERIAL, "for");

        assertThat(forecast.chance("for")).isCloseTo(0.5, within(0.2));
        assertThat(withRider.chance("for")).isLessThan(0.1);
    }

    @Test
    void anImperialRiderNobodyIsRacingAgainstDoesNotMoveTheVoters() {
        test.game.setCurrentAgendaInfo(ARMS_REDUCTION);
        votes(3, 3, 0);
        points(test.sol, 1);
        AgendaForecast forecast = forecast();

        AgendaForecast withRider = forecast.withRider(xxcha, RiderPayoff.IMPERIAL, "for");

        assertThat(withRider.chance("for")).isCloseTo(forecast.chance("for"), within(0.3));
    }

    @Test
    void isEmptyInHiddenAgendaMode() {
        test.game.setHiddenAgendaMode(true);

        assertThat(forecast().isEmpty()).isTrue();
        assertThat(forecast().chances()).isEqualTo(Map.of());
    }

    @Test
    void isEmptyOutsideTheAgendaPhaseAndForAnAgendaItCannotEnumerate() {
        test.game.setCurrentAgendaInfo("Directive_Elect Strategy Card_7_crisis");
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
