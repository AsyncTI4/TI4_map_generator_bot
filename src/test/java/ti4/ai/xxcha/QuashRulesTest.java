package ti4.ai.xxcha;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Quash spends a strategy token to discard the revealed agenda and reveal the next one, so the seat only plays it
// when the agenda it has in front of it is expected to cost more than that token is worth.
class QuashRulesTest extends BaseTi4Test {

    private static final String THIRD_ID = "200000000000000002";

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        sol = test.sol;
        Player rival = test.addSeat(THIRD_ID, "xxcha", "red");
        test.game.setVp(10);
        test.nekro.addAbility("quash");
        test.nekro.setStrategicCC(2);
        test.game.setPhaseOfGame("agendawaiting");
        test.game.setCurrentAgendaInfo("Law_Elect Player_7_shard_of_the_throne");
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":9,\"red\":4,\"black\":0}");
        givePoints(sol, 7);
        givePoints(rival, 1);
    }

    private void givePoints(Player player, int points) {
        test.game.scorePublicObjective(
                player.getUserID(), test.game.addCustomPO("Test points " + player.getFaction(), points));
    }

    private static AiPrompt whenOffer() {
        return prompt("offer", PromptSource.AI_THREAD, NOW, "queueAWhen", "declineToQueueAWhen", "explainQueue");
    }

    private static AiPrompt whenOptions() {
        return prompt("options", PromptSource.AI_THREAD, NOW, "queueWhen_ability_quash", "declineToQueueAWhen");
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // Sol, one point from the lead of a Shard of the Throne election and holding the most votes, would hand itself a
    // point: worth far more than the token.
    @Test
    void quashesAnElectionThatHandsARivalAPoint() {
        assertThat(pressed(QuashRules.queue(test.context(whenOffer())))).isEqualTo("queueAWhen");
        assertThat(pressed(QuashRules.queue(test.context(whenOptions())))).isEqualTo("queueWhen_ability_quash");
    }

    @Test
    void keepsTheAgendaWhenTheRivalHasNoVotes() {
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":0,\"red\":4,\"black\":0}");

        assertThat(QuashRules.worthQuashing(test.game, test.nekro)).isFalse();
        assertThat(QuashRules.queue(test.context(whenOffer()))).isEmpty();
    }

    // An agenda the AI has no view on is not expected to cost anything, so the token stays in the pool.
    @Test
    void keepsAnAgendaItHasNoViewOn() {
        test.game.setCurrentAgendaInfo("Directive_For/Against_7_arms_reduction");

        assertThat(QuashRules.queue(test.context(whenOffer()))).isEmpty();
    }

    @Test
    void needsAStrategyToken() {
        test.nekro.setStrategicCC(0);

        assertThat(QuashRules.queue(test.context(whenOffer()))).isEmpty();
    }

    @Test
    void needsTheAbility() {
        test.nekro.removeAbility("quash");

        assertThat(QuashRules.queue(test.context(whenOffer()))).isEmpty();
    }

    @Test
    void doesNotQueueOverAQueuedEntry() {
        test.game.setStoredValue("queuedWhens", "nekro_");
        test.game.setStoredValue("queuedWhensFornekro", "pn_blue_ps");

        assertThat(QuashRules.queue(test.context(whenOffer()))).isEmpty();
    }

    @Test
    void doesNothingOnceTheWhensAreResolved() {
        test.game.setStoredValue("whensResolved", "Yes");

        assertThat(QuashRules.queue(test.context(whenOffer()))).isEmpty();
    }

    @Test
    void theBrainQuashesBeforePassingOnWhens() {
        assertThat(AiTestGame.pressedId(new NekroBrain().decide(test.context(whenOffer()))))
                .isEqualTo("queueAWhen");
    }

    @Test
    void theBrainPassesOnWhensWhenTheAgendaIsHarmless() {
        test.game.setStoredValue("agendaStartVoteCounts", "{\"blue\":0,\"red\":4,\"black\":0}");

        assertThat(AiTestGame.pressedId(new NekroBrain().decide(test.context(whenOffer()))))
                .isEqualTo("declineToQueueAWhen");
    }
}
