package ti4.ai.nekro;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class NekroRulesTest extends BaseTi4Test {

    private static final List<String> GAIN_BUTTONS =
            List.of("FFCC_nekro_increase_tactic_cc", "FFCC_nekro_increase_fleet_cc", "FFCC_nekro_increase_strategy_cc");

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
    }

    private AiPrompt gainPrompt(String content) {
        List<String> ids = new java.util.ArrayList<>(GAIN_BUTTONS);
        ids.add("FFCC_nekro_deleteButtons");
        List<String> labels = List.of("Gain Tactic", "Gain Fleet", "Gain Strategy", "Done Gaining Command Tokens");
        AiPrompt prompt = AiTestGame.prompt("gain", PromptSource.PUBLIC, NOW, ids, labels);
        return AiTestGame.withContent(prompt, content);
    }

    private String press(AiPrompt prompt) {
        return NekroRules.propagationTokens(test.context(prompt))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    // The bot replaces the prompt's text after each token ("command tokens have gone from ..."), so the AI has to
    // remember the prompt to collect all of Propagation's tokens and then press Done.
    @Test
    void gainsEveryPropagationTokenAfterTheBotRewritesThePrompt() {
        String announced = test.nekro.getRepresentation()
                + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens.";
        assertThat(press(gainPrompt(announced))).startsWith("FFCC_nekro_increase_");

        test.nekro.setStrategicCC(3);
        AiPrompt rewritten =
                gainPrompt(test.nekro.getRepresentation() + " command tokens have gone from 3/3/2 -> 3/3/3.");
        assertThat(press(rewritten)).startsWith("FFCC_nekro_increase_");

        test.nekro.setTacticalCC(4);
        test.nekro.setStrategicCC(4);
        assertThat(press(rewritten)).isEqualTo("FFCC_nekro_deleteButtons");
    }

    // Propagation can't take the seat past its 16 command tokens.
    @Test
    void stopsGainingAtTheCommandTokenLimit() {
        test.nekro.setTacticalCC(8);
        test.nekro.setFleetCC(5);
        test.nekro.setStrategicCC(3);
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        String announced = test.nekro.getRepresentation()
                + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens.";

        assertThat(press(gainPrompt(announced))).isEqualTo("FFCC_nekro_deleteButtons");
    }
}
