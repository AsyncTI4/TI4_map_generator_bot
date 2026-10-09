package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
