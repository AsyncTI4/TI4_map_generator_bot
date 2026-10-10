package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class AiRuntimeTest extends BaseTi4Test {

    private static final String GAME = "ai-runtime-test";

    // /ai watch saves a new game in two steps. A tick that read the first save finds no AI seats and drops the lane it
    // was running; if that happens after the game was registered again, it must not drop the new lane.
    @Test
    void aStaleTickDoesNotForgetTheGameRegisteredAgain() {
        try {
            AiRuntime.register(GAME);
            AiLane stale = new AiLane(GAME);
            AiRuntime.forget(GAME);
            AiRuntime.register(GAME);

            AiRuntime.forget(stale);

            assertThat(AiRuntime.isTracked(GAME)).isTrue();
        } finally {
            AiRuntime.forget(GAME);
        }
    }
}
