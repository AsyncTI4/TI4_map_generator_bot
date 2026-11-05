package ti4.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService.ShutdownStep;

class JdaServiceShutdownStepsTest {

    @Test
    void aFailingStepDoesNotSkipTheStepsAfterIt() {
        // Before, one exception (e.g. a BotLogger webhook RejectedExecutionException once CronManager was shut
        // down) skipped every later step, including draining the pipelines and releasing the active lease.
        List<String> ran = new ArrayList<>();

        JdaService.runShutdownSteps(List.of(
                new ShutdownStep("first", () -> ran.add("first")),
                new ShutdownStep("failing", () -> {
                    throw new RejectedExecutionException("simulated");
                }),
                new ShutdownStep("release active lease", () -> ran.add("release active lease"))));

        assertThat(ran).containsExactly("first", "release active lease");
    }
}
