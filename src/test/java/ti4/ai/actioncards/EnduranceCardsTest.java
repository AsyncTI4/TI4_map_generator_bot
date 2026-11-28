package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.endurance.Endurance;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// A component action card that is only a stall (Pirate Contract here) is the stall that wins Prove Endurance, so a
// seat holding the secret keeps it until it can out-stall the rest of the table.
class EnduranceCardsTest extends BaseTi4Test {

    private static final int HAND_INDEX = 412;

    private CardTable table;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.system("40", "310");
        table.nekro.setActionCard("piratecontract3", HAND_INDEX);
        table.nekro.setSecret(Endurance.SECRET);
        table.nekro.setStrategicCC(2);
    }

    private String playsBeforePassing() {
        AiPrompt hand = prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
        return ActionCardRules.playBeforePassing(table.test.context(hand))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    @Test
    void playsTheStallWhenTheRestOfTheTableCannotOutlastIt() {
        table.nekro.setTacticalCC(3);
        table.sol.setTacticalCC(1);

        assertThat(playsBeforePassing()).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void keepsTheStallWhileAnotherPlayerHasFarMoreActions() {
        table.nekro.setTacticalCC(0);
        table.sol.setTacticalCC(6);

        assertThat(playsBeforePassing()).isEmpty();
    }

    @Test
    void playsTheStallAsBeforeWithoutTheSecret() {
        table.nekro.removeSecret(table.nekro.getSecrets().get(Endurance.SECRET));
        table.nekro.setTacticalCC(0);
        table.sol.setTacticalCC(6);

        assertThat(playsBeforePassing()).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }
}
