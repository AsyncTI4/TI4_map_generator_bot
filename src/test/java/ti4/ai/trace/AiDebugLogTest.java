package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.trace.AiDebugLog.Mode;
import ti4.testUtils.BaseTi4Test;

class AiDebugLogTest extends BaseTi4Test {

    @Test
    void isOffUntilTurnedOnAndOffAgain() {
        AiTestGame test = new AiTestGame();
        assertThat(AiDebugLog.mode(test.game)).isEqualTo(Mode.OFF);

        AiDebugLog.setMode(test.game, Mode.PUBLIC);
        assertThat(AiDebugLog.mode(test.game)).isEqualTo(Mode.PUBLIC);

        AiDebugLog.setMode(test.game, Mode.OFF);
        assertThat(AiDebugLog.isOn(test.game)).isFalse();
    }

    // Prompts the AI answers often ping players; copying those into the debug thread would pull them into it.
    @Test
    void stripsPingsFromWhatItPosts() {
        String line = AiDebugLog.withoutPings("press done on \"<@123456> <@!42> <@&7> @everyone @here your turn\"");

        assertThat(line).isEqualTo("press done on \"@someone @someone @someone everyone here your turn\"");
    }
}
