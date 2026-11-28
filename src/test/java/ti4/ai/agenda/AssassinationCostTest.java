package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The user's rule (2026-11-21): Assassinate Representative costs what the agenda's result cost the player it
// silenced, next to the best result its own votes could have bought; under 1 TG (or a gain) it was not mean at all.
//
// The table: Public Execution with every vote in. Xxcha's 6 votes elect Sol, the speaker, whose execution costs it
// the speaker token. Sol was assassinated, so its own votes never counted.
class AssassinationCostTest extends BaseTi4Test {

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
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo(EXECUTION);
        test.game.setSpeakerUserID(sol.getUserID());
        test.game.setStoredValue("AssassinatedReps", "sol");
        test.game.setCurrentAgendaVote("sol", "red_6");
        test.game.setPhaseOfGame("agendaEnd");
    }

    // Without votes to cast Sol could not have changed anything, so silencing it cost nothing.
    @Test
    void silencingAPlayerWithoutVotesCostsNothing() {
        assertThat(AssassinationCost.whenVotesAreIn(test.game, sol).orElseThrow())
                .isZero();
    }

    // With enough votes of its own Sol would have elected someone else; silencing it cost it its own execution.
    @Test
    void silencingAPlayerWhoseVotesWouldHaveSavedItCostsTheDifference() {
        test.place("18", "000");
        sol.addPlanet("mr");
        sol.addPlanet("archonren");
        xxcha.removePlanet("archonren");

        assertThat(AssassinationCost.whenVotesAreIn(test.game, sol).orElseThrow())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void nothingIsJudgedBeforeTheVotesAreIn() {
        test.game.setPhaseOfGame("agendaVoting");
        assertThat(AssassinationCost.whenVotesAreIn(test.game, sol)).isEmpty();
    }
}
