package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The Mutiny table from self-play game aiselfplay337972 (round 1, first agenda): Xxcha 6, Sardakk 4, Hacan 9,
// Letnev 5 and Sol 5 votes, voting in that order with Sol the speaker last; Xxcha and Hacan hold 1 point each and
// Nekro cannot vote. In the game Xxcha, Sardakk and Hacan each cast 1-3 votes For at 93-100%, and Sol tied it 5-5
// Against and broke the tie Against, costing Xxcha and Hacan their point.
class MutinyForecastTest extends BaseTi4Test {

    private static final String MUTINY = "Directive_For/Against_7_mutiny";

    private AiTestGame test;
    private Player xxcha;
    private Player sardakk;
    private Player hacan;
    private Player letnev;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat("200000000000000002", "xxcha", "red");
        sardakk = test.addSeat("200000000000000003", "sardakk", "orange");
        hacan = test.addSeat("200000000000000004", "hacan", "yellow");
        letnev = test.addSeat("200000000000000005", "letnev", "purple");
        test.game.setVp(10);
        test.game.setRound(1);
        test.game.setStoredValue("agendaCount", "1");
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.points(xxcha, 1);
        test.points(hacan, 1);
        test.game.setCurrentAgendaInfo(MUTINY);
        test.game.setStoredValue(
                "agendaStartVoteCounts", "{\"red\":6,\"orange\":4,\"yellow\":9,\"purple\":5,\"blue\":5,\"black\":0}");
        test.game.setPhaseOfGame("agendaVoting");
    }

    private void turnOf(Player seat) {
        test.game.setActivePlayerID(seat.getUserID());
    }

    private void voted(Player seat, String outcome, int votes) {
        String earlier = test.game.getCurrentAgendaVotes().getOrDefault(outcome, "");
        test.game.setCurrentAgendaVote(
                outcome, (earlier.isEmpty() ? "" : earlier + ";") + seat.getColor() + "_" + votes);
    }

    private double forChance(Player seat, int votes) {
        return AgendaForecast.of(test.game, seat).withVotes(seat, "for", votes).chance("for");
    }

    // Why the table was so sure: the old forecast had every later seat cast its whole stack, so the speaker's 5 votes
    // Against were always outweighed by the For stacks it assumed for Sardakk, Hacan and Letnev, none of which those
    // seats actually cast. Xxcha's single vote looked as safe as six.
    @Test
    void theOldForecastLeanedOnAlliesCastingTheirWholeStack() {
        turnOf(xxcha);
        AiExperiments.disable(xxcha, Set.of(Experiment.AGENDA_HELD_BALLOTS, Experiment.AGENDA_ABSTAIN_CHOICE));

        assertThat(forChance(xxcha, 1)).isGreaterThan(0.95);
        assertThat(forChance(xxcha, 5) - forChance(xxcha, 1)).isLessThan(0.05);
    }

    // Now each later seat casts only what holds its outcome against the votes still uncast, and the speaker answers
    // the tally, so one vote For is plainly less safe than five.
    @Test
    void aOneVoteBallotIsNotSafeWhenTheSpeakerVotesLast() {
        turnOf(xxcha);

        assertThat(forChance(xxcha, 1)).isLessThan(0.85);
        assertThat(forChance(xxcha, 5)).isGreaterThan(forChance(xxcha, 1) + 0.05);
    }

    // Each For vote makes Against better for the seats after it (a For voter with a point loses it), so after Xxcha
    // and Sardakk voted, the speaker and Letnev lean Against once Hacan joins. Hacan's single vote For is a coin flip,
    // while all nine put For beyond the ten votes Letnev and Sol have left.
    @Test
    void eachForVoteMakesAgainstBetterForTheSeatsAfterIt() {
        voted(xxcha, "for", 1);
        voted(sardakk, "for", 1);
        turnOf(hacan);

        assertThat(forChance(hacan, 1)).isLessThan(0.75);
        assertThat(forChance(hacan, 9)).isEqualTo(1.0);
    }

    // The trace's own ending: three seats put one vote each on For and Letnev abstains. Sol votes last, sees For 3 with
    // two point holders on it, and casts what ties or beats it Against (it breaks the tie itself), so Against is
    // certain. Before the speaker answered the tally it was a draw from Sol's joining values.
    @Test
    void theSpeakerAnswersTheTallyAndPunishesTheForVoters() {
        voted(xxcha, "for", 1);
        voted(sardakk, "for", 1);
        voted(hacan, "for", 1);
        turnOf(letnev);

        double answering = AgendaForecast.of(test.game, letnev).without(letnev).chance("against");
        AiExperiments.disable(letnev, Set.of(Experiment.AGENDA_ABSTAIN_CHOICE));
        double drawn = AgendaForecast.of(test.game, letnev).without(letnev).chance("against");

        assertThat(answering).isEqualTo(1.0);
        assertThat(drawn).isLessThan(answering);
    }

    // A seat on For with a point is worth less to its rivals on For (it gains one too) and more to them on Against
    // (it loses it); a seat on 0 points loses nothing when Against wins.
    @Test
    void aForVoterShiftsTheOthersUtilities() {
        assertThat(DirectiveValues.mutinyForVoterJoins(test.game, test.sol, hacan, "for"))
                .isNegative();
        assertThat(DirectiveValues.mutinyForVoterJoins(test.game, test.sol, hacan, "against"))
                .isPositive();
        assertThat(DirectiveValues.mutinyForVoterJoins(test.game, test.sol, letnev, "against"))
                .isZero();
        assertThat(DirectiveValues.mutinyForVoterJoins(test.game, hacan, hacan, "for"))
                .isZero();
    }

    // A seat that does not vote (it placed a rider, say) gets no point when For wins: its own-vote value says so.
    @Test
    void abstainingOnAForWinGivesUpTheForPoint() {
        turnOf(xxcha);
        AgendaForecast forecast = AgendaForecast.of(test.game, hacan).withVotes(xxcha, "for", 30);
        double point = ti4.ai.scoring.ScoringStakes.scoreValue(test.game, hacan, 1);

        assertThat(forecast.chance("for")).isGreaterThan(0.99);
        assertThat(forecast.expectedUtility(hacan, "for") - forecast.expectedUtility(hacan, ""))
                .isCloseTo(point, org.assertj.core.api.Assertions.within(0.05 * point));
        assertThat(forecast.expectedUtility(hacan, "for"))
                .isCloseTo(forecast.expectedUtility(hacan), org.assertj.core.api.Assertions.within(1e-9));
    }
}
