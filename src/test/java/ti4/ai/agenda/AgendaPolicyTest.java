package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class AgendaPolicyTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    private void givePoints(String userId, int points) {
        test.game.scorePublicObjective(userId, test.game.addCustomPO("Test points", points));
    }

    private Optional<String> tieBreak(String agenda, String... customIds) {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_" + agenda);
        AiPrompt tie = prompt("tie", PromptSource.PUBLIC, NOW, customIds);
        return AgendaPolicy.chooseOutcome(test.game, test.nekro, tie.enabledButtons())
                .map(PromptButton::customId);
    }

    private void shardRevealed(int solVotes, int nekroVotes) {
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":" + solVotes + ",\"black\":" + nekroVotes + "}");
    }

    // The seat is expected to be elected to a point when it is the favourite in the forecast, which also counts what
    // the other voters will do with their votes.
    @Test
    void expectsToBeElectedToAPointWhenItIsTheFavourite() {
        shardRevealed(0, 0);
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":2,\"black\":0}");
        assertThat(AgendaPolicy.wouldElectToAPoint(test.game, test.sol)).isTrue();
        assertThat(AgendaPolicy.wouldElectToAPoint(test.game, test.nekro)).isFalse();
    }

    @Test
    void expectsNoPointFromAnAgendaThatGivesNone() {
        shardRevealed(2, 0);
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_minister_war");

        assertThat(AgendaPolicy.wouldElectToAPoint(test.game, test.sol)).isFalse();
    }

    // Without a usable forecast (no agenda phase) the predictions read the votes at the start of the agenda.
    @Test
    void fallsBackOnTheVotesWhenThereIsNoForecast() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":5}");

        assertThat(AgendaPolicy.wouldElectToAPoint(test.game, test.sol)).isTrue();
    }

    // A voter silenced by a Political Secret has no votes in the prediction, so the leader's grip on a point election
    // loosens and the seat expects a better election.
    @Test
    void anElectionScoreImprovesWhenTheLeadingRivalIsSilenced() {
        test.addSeat("200000000000000002", "xxcha", "red");
        shardRevealed(9, 0);
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":4,\"black\":0}");
        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO("Test points", 6));

        assertThat(AgendaPolicy.predictedElectionScore(test.game, test.nekro, test.sol))
                .isGreaterThan(AgendaPolicy.predictedElectionScore(test.game, test.nekro, null));
    }

    // The speaker breaking a tie keeps a rival's Imperial Rider from winning the rival the game.
    @Test
    void breaksATieAgainstARivalsImperialRider() {
        Player rival = test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setVp(10);
        givePoints(rival.getUserID(), 9);
        test.game.setPhaseOfGame("agendaVoting");
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":3,\"red\":0,\"black\":0}");
        AiPrompt tie = prompt(
                "tie",
                PromptSource.PUBLIC,
                NOW,
                "resolveAgendaVote_outcomeTie*_for",
                "resolveAgendaVote_outcomeTie*_against");

        assertThat(AgendaPolicy.chooseOutcome(test.game, test.nekro, tie.enabledButtons())
                        .map(PromptButton::customId))
                .contains("resolveAgendaVote_outcomeTie*_for");

        test.game.setCurrentAgendaVote("for", "red_Imperial Rider");
        assertThat(AgendaPolicy.chooseOutcome(test.game, test.nekro, tie.enabledButtons())
                        .map(PromptButton::customId))
                .contains("resolveAgendaVote_outcomeTie*_against");
    }

    // With nobody voting, the speaker decides: an agenda that hands the elected player a victory point goes to
    // the speaker itself.
    @Test
    void electsItselfForAVictoryPointAgenda() {
        assertThat(tieBreak(
                        "shard_of_the_throne",
                        "resolveAgendaVote_outcomeTie*_sol",
                        "resolveAgendaVote_outcomeTie*_nekro"))
                .contains("resolveAgendaVote_outcomeTie*_nekro");
    }

    // A harmful election goes to someone else, preferring the leader.
    @Test
    void neverElectsItselfForPublicExecution() {
        givePoints(test.sol.getUserID(), 4);

        assertThat(tieBreak("execution", "resolveAgendaVote_outcomeTie*_nekro", "resolveAgendaVote_outcomeTie*_sol"))
                .contains("resolveAgendaVote_outcomeTie*_sol");
    }

    // Seed of an Empire gives the leader a point "for" and the trailing player a point "against".
    @Test
    void picksTheSeedOfAnEmpireSideThatScoresForItself() {
        givePoints(test.sol.getUserID(), 5);
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_seed_empire");
        AiPrompt tie = prompt(
                "tie",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_resolveAgendaVote_outcomeTie* for",
                "FFCC_nekro_resolveAgendaVote_outcomeTie* against");

        assertThat(AgendaPolicy.chooseOutcome(test.game, test.nekro, tie.enabledButtons())
                        .map(PromptButton::customId))
                .contains("FFCC_nekro_resolveAgendaVote_outcomeTie* against");
    }

    @Test
    void keepsTheFirstOutcomeForAnAgendaWithoutAPreference() {
        assertThat(tieBreak(
                        "arms_reduction", "resolveAgendaVote_outcomeTie*_for", "resolveAgendaVote_outcomeTie*_against"))
                .contains("resolveAgendaVote_outcomeTie*_for");
    }
}
