package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Milestone 4b: Conventions of War Against only costs the seats that voted Against (they discard their hand), and the
// speaker's tie-break is not a vote. Sol is the speaker and votes last, Xxcha votes before it, Nekro cannot vote.
class AbstainChoiceForecastTest extends BaseTi4Test {

    private static final String CONVENTIONS = "Law_For/Against_7_conventions";
    private static final String TIE = "resolveAgendaVote_outcomeTie*_";

    private AiTestGame test;
    private Player sol;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        sol = test.sol;
        xxcha = test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setVp(10);
        test.game.setRound(2);
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setStoredValue("agendaCount", "2");
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setCurrentAgendaInfo(CONVENTIONS);
        sol.setActionCard("sabo1", 1);
        sol.setActionCard("sabo2", 2);
        sol.setActionCard("veto", 3);
        for (int index = 1; index <= 3; index++) xxcha.setActionCard("examcard" + index, index);
        votes(3, 3);
    }

    private void votes(int sol, int xxcha) {
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":" + sol + ",\"red\":" + xxcha + ",\"black\":0}");
    }

    private Optional<String> tieBreak() {
        AiPrompt tie = prompt("tie", PromptSource.PUBLIC, NOW, TIE + "for", TIE + "against");
        return AgendaPolicy.chooseOutcome(test.game, sol, tie.enabledButtons())
                .map(PromptButton::customId)
                .map(id -> id.substring(TIE.length()));
    }

    // Joining Against costs Sol its hand, but picking Against on a tie costs it nothing, since it did not vote.
    @Test
    void theSpeakersTieBreakIsNotAVote() {
        AgendaForecast forecast = AgendaForecast.of(test.game, sol);

        assertThat(forecast.utility(sol, "against")).isNegative();
        assertThat(forecast.tieBreakUtility(sol, "against")).isZero();
    }

    // A speaker that voted Against itself does pay for it when Against wins the tie.
    @Test
    void aSpeakerThatVotedAgainstPaysForItOnTheTieBreak() {
        test.game.setCurrentAgendaVote("against", "blue_1");

        assertThat(AgendaForecast.of(test.game, sol).tieBreakUtility(sol, "against"))
                .isNegative();
    }

    // When both outcomes are worth the same to a speaker that did not vote, it picks Against: on these agendas that is
    // the outcome no voter pays for. Without the experiment the first outcome listed wins the even tie.
    @Test
    void anEvenTieGoesAgainstWithTheAbstainExperiment() {
        votes(0, 0);

        assertThat(tieBreak()).contains("against");

        AiExperiments.disable(sol, Set.of(Experiment.AGENDA_ABSTAIN_CHOICE));
        assertThat(tieBreak()).contains("for");
    }

    // Xxcha gains nothing from For and would lose its hand voting Against, so it abstains and leaves the outcome to
    // Sol, which votes last, sees nobody voted and picks Against for free. The old forecast had Xxcha vote For on its
    // joining values (For 0 against Against minus its hand), so For usually won.
    @Test
    void theTableAbstainsAndTheSpeakerPicksAgainst() {
        assertThat(AgendaForecast.of(test.game, test.nekro).chance("against")).isGreaterThan(0.95);

        AiExperiments.disable(test.nekro, Set.of(Experiment.AGENDA_ABSTAIN_CHOICE));
        assertThat(AgendaForecast.of(test.game, test.nekro).chance("against")).isLessThan(0.5);
    }

    // The speaker answers the tally: if Xxcha has voted For with 3, Sol (3 votes) ties it Against only if that is
    // worth its hand; here For costs Sol nothing, so it lets For pass rather than discard.
    @Test
    void theSpeakerDoesNotPayItsHandToBeatAnOutcomeItDoesNotMind() {
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setActivePlayerID(sol.getUserID());
        test.game.setCurrentAgendaVote("for", "red_3");

        assertThat(AgendaForecast.of(test.game, test.nekro).chance("for")).isEqualTo(1.0);
    }
}
