package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class GalacticThreatRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("agendawaiting");
        test.sol.addTech("gd");
    }

    private String press(AiPrompt... prompts) {
        return GalacticThreatRules.next(test.context(prompts))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    // Nekro cannot vote, but its free prediction can win it a technology: it queues Galactic Threat as an "after".
    @Test
    void queuesGalacticThreatInTheAfterWindow() {
        AiPrompt window = prompt("window", PromptSource.AI_THREAD, NOW, "queueAnAfter", "declineToQueueAnAfter");
        assertThat(press(window)).isEqualTo("queueAnAfter");

        AiPrompt options = prompt(
                "options", PromptSource.AI_THREAD, NOW, "queueAfter_ability_galactic_threat", "declineToQueueAnAfter");
        assertThat(press(options)).isEqualTo("queueAfter_ability_galactic_threat");
    }

    // While the prediction waits in the queue, the decline buttons the bot posts would cancel it.
    @Test
    void holdsAQueuedPredictionUntilItIsUsed() {
        test.game.setStoredValue("queuedAfters", "nekro_");

        assertThat(GalacticThreatRules.holdsQueuedPrediction(test.game, test.nekro))
                .isTrue();

        test.game.setStoredValue("galacticThreatUsed", "Yes");
        assertThat(GalacticThreatRules.holdsQueuedPrediction(test.game, test.nekro))
                .isFalse();
    }

    // When the prediction comes up, it predicts the player with the most votes being elected.
    @Test
    void predictsTheElectionOfThePlayerWithTheMostVotes() {
        test.game.setStoredValue("galacticThreatUsed", "Yes");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":7}");
        AiPrompt riders = prompt(
                "riders",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_rider_player;nekro_Galactic Threat Rider",
                "FFCC_nekro_rider_player;sol_Galactic Threat Rider");

        assertThat(press(riders)).isEqualTo("FFCC_nekro_rider_player;sol_Galactic Threat Rider");
    }

    // With the agenda in view the prediction follows the forecast, which counts the votes a rider has already taken
    // out of the election: Sol has the most votes at the start, but its own rider is on Xxcha. Sol is the speaker, so
    // Xxcha cannot leave its election to a tie-break and must cast votes for it.
    @Test
    void predictsTheOutcomeTheForecastFavoursNotJustTheMostVotes() {
        test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("galacticThreatUsed", "Yes");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":4,\"black\":0}");
        AiPrompt riders = prompt(
                "riders",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_rider_player;sol_Galactic Threat Rider",
                "FFCC_nekro_rider_player;xxcha_Galactic Threat Rider");
        assertThat(press(riders)).isEqualTo("FFCC_nekro_rider_player;sol_Galactic Threat Rider");

        test.game.setCurrentAgendaVote("xxcha", "blue_Technology Rider");
        assertThat(press(riders)).isEqualTo("FFCC_nekro_rider_player;xxcha_Galactic Threat Rider");
    }

    // A planet rider is aimed at a player's planets: the player whose planets are most likely to be elected.
    @Test
    void predictsAPlanetOwnerByTheChanceOfItsPlanetsWinning() {
        test.addSeat("200000000000000002", "xxcha", "red");
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.game.setStoredValue("galacticThreatUsed", "Yes");
        test.game.setCurrentAgendaInfo("Directive_Elect Planet_7_disarmament");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":4,\"black\":0}");
        AiPrompt riders = prompt(
                "riders",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_planetRider_xxcha_Galactic Threat Rider",
                "FFCC_nekro_planetRider_sol_Galactic Threat Rider");

        assertThat(press(riders)).isEqualTo("FFCC_nekro_planetRider_sol_Galactic Threat Rider");
    }

    // A Political Secret played on Nekro bars its faction abilities until the agenda is resolved; pressing
    // queueAnAfter would also take Nekro back out of the declined afters the bot put it in.
    @Test
    void queuesNoGalacticThreatWhileSilencedByPoliticalSecret() {
        test.game.setStoredValue("AssassinatedReps", "nekro");
        test.game.setStoredValue("declinedAfters", "nekro_");
        AiPrompt window = prompt("window", PromptSource.AI_THREAD, NOW, "queueAnAfter", "declineToQueueAnAfter");

        assertThat(press(window)).isEmpty();
    }

    // Assassinate Representative only takes away Nekro's votes, which it never casts anyway; its faction abilities,
    // Galactic Threat included, still work.
    @Test
    void stillQueuesGalacticThreatAfterAssassinateRepresentative() {
        test.game.setStoredValue("AssassinatedReps", "nekro");
        AiPrompt window = prompt("window", PromptSource.AI_THREAD, NOW, "queueAnAfter", "declineToQueueAnAfter");

        assertThat(press(window)).isEqualTo("queueAnAfter");
    }

    private void openMinisterOfWar(String agendaCount) {
        test.addSeat("200000000000000002", "xxcha", "red");
        test.addSeat("200000000000000003", "hacan", "green");
        test.game.setVp(10);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaCount", agendaCount);
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_minister_war");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":6,\"red\":6,\"green\":6,\"black\":0}");
    }

    // Traces-4b, Minister of War on the first agenda: four candidates and no seat sure to vote any of them. Nekro keeps
    // Galactic Threat for the second agenda rather than spend it on a 14% guess.
    @Test
    void keepsGalacticThreatForTheSecondAgendaOnAnOpenElection() {
        openMinisterOfWar("1");
        AiPrompt window = prompt("window", PromptSource.AI_THREAD, NOW, "queueAnAfter", "declineToQueueAnAfter");

        assertThat(GalacticThreatRules.suitsThisAgenda(test.game, test.nekro)).isFalse();
        assertThat(press(window)).isEmpty();
    }

    // Nekro uses Galactic Threat every agenda phase: unused on the first agenda, it goes on the second whatever it is.
    @Test
    void usesGalacticThreatOnTheSecondAgendaWhateverItIs() {
        openMinisterOfWar("2");
        AiPrompt window = prompt("window", PromptSource.AI_THREAD, NOW, "queueAnAfter", "declineToQueueAnAfter");

        assertThat(press(window)).isEqualTo("queueAnAfter");
    }

    // A For/Against agenda has two outcomes, so even an unsure prediction is a coin flip worth taking on the first
    // agenda.
    @Test
    void usesGalacticThreatOnAFirstAgendaWithTwoOutcomes() {
        test.game.setVp(10);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo("Law_For/Against_7_sanctions");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":6,\"black\":0}");

        assertThat(GalacticThreatRules.suitsThisAgenda(test.game, test.nekro)).isTrue();
    }

    // Without the timing experiment Nekro predicts on the first agenda, as before.
    @Test
    void controlSeatPredictsOnTheFirstAgenda() {
        openMinisterOfWar("1");
        AiExperiments.disable(test.nekro, Set.of(Experiment.GALACTIC_THREAT_TIMING));

        assertThat(GalacticThreatRules.suitsThisAgenda(test.game, test.nekro)).isTrue();
    }

    // A first-agenda election that one seat is sure to carry with its own votes: Nekro predicts it right away.
    @Test
    void usesGalacticThreatOnAFirstAgendaElectionWithASureVoter() {
        test.addSeat("200000000000000002", "xxcha", "red");
        test.game.setVp(10);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_minister_war");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":12,\"red\":1,\"black\":0}");

        assertThat(GalacticThreatRules.suitsThisAgenda(test.game, test.nekro)).isTrue();
    }
}
