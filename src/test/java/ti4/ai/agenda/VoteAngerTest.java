package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiPersonality;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The user's rule (2026-11-16): voting for an outcome that hurts another player makes that player mad, so the AI
// weighs a cost for it, as it does for attacks. Bad relations drop the cost (they are mad already), and so does a
// player who must be stopped (near the goal or running away with the lead).
//
// The table: Public Execution. Sol (an AI) is the speaker with four action cards; executing it costs it the hand,
// the speaker token and its votes. Xxcha (an AI, 6 votes) is the voter whose ballot is weighed.
class VoteAngerTest extends BaseTi4Test {

    private static final String EXECUTION = "Directive_Elect Player_7_execution";

    private AiTestGame test;
    private Player sol;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        xxcha = test.addSeat("7100000555555555", "xxcha", "red");
        test.addSeat("7100000666666666", "hacan", "yellow");
        test.game.setVp(10);
        test.place("14", "310");
        test.place("19", "311");
        for (String planet : List.of("archonren", "archontau", "wellon")) xxcha.addPlanet(planet);
        for (String card : List.of("sabo1", "sabo2", "overrule", "bribery")) sol.setActionCard(card);
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo(EXECUTION);
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":0,\"red\":6,\"yellow\":0,\"black\":0}");
        test.isActive(xxcha, "agendaVoting");
    }

    private double angerAtExecutingSol() {
        return VoteAnger.ofBallot(test.game, xxcha, AgendaForecast.of(test.game, xxcha), "sol", 6);
    }

    @Test
    void electingAPlayerForExecutionAngersThem() {
        assertThat(VoteAnger.harm(AgendaForecast.of(test.game, xxcha), sol, "sol"))
                .isPositive();
        assertThat(angerAtExecutingSol()).isPositive();
    }

    @Test
    void abstainingAngersNobody() {
        assertThat(VoteAnger.ofBallot(test.game, xxcha, AgendaForecast.of(test.game, xxcha), "sol", 0))
                .isZero();
    }

    // A WARRIOR starts every player in bad relations, so it votes against them without fearing their anger.
    @Test
    void badRelationsDropTheCost() {
        AiPersonality.set(xxcha, AiPersonality.WARRIOR);
        assertThat(angerAtExecutingSol()).isZero();
    }

    // Sol is 2 points from the goal, so stopping it is worth its anger.
    @Test
    void aPlayerNearTheGoalMustBeStopped() {
        test.points(sol, 8);
        assertThat(angerAtExecutingSol()).isZero();
    }
}
