package ti4.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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

    @Test
    void parallelStepsRunAtTheSameTime() {
        // Each step waits for the other to start. Run one after another, the first step would time out alone.
        CountDownLatch bothStarted = new CountDownLatch(2);
        List<Boolean> sawTheOtherStart = new CopyOnWriteArrayList<>();
        Runnable awaitOther = () -> {
            bothStarted.countDown();
            try {
                sawTheOtherStart.add(bothStarted.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };

        JdaService.runShutdownSteps(List.of(
                JdaService.parallel("pair", new ShutdownStep("a", awaitOther), new ShutdownStep("b", awaitOther))));

        assertThat(sawTheOtherStart).containsExactly(true, true);
    }

    @Test
    void theNextStepWaitsForEveryParallelStepToFinish() {
        // Persistence pipelines must not shut down while a producer phase can still save a game.
        List<String> ran = new CopyOnWriteArrayList<>();
        Runnable slow = () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            ran.add("slow producer");
        };

        JdaService.runShutdownSteps(List.of(
                JdaService.parallel(
                        "producers",
                        new ShutdownStep("slow", slow),
                        new ShutdownStep("fast", () -> ran.add("fast producer"))),
                new ShutdownStep("consumer", () -> ran.add("consumer"))));

        assertThat(ran).containsExactlyInAnyOrder("slow producer", "fast producer", "consumer");
        assertThat(ran.getLast()).isEqualTo("consumer");
    }

    @Test
    void aFailingParallelStepDoesNotStopItsSiblings() {
        List<String> ran = new CopyOnWriteArrayList<>();

        JdaService.runShutdownSteps(List.of(
                JdaService.parallel(
                        "phase",
                        new ShutdownStep("failing", () -> {
                            throw new RejectedExecutionException("simulated");
                        }),
                        new ShutdownStep("sibling", () -> ran.add("sibling"))),
                new ShutdownStep("after", () -> ran.add("after"))));

        assertThat(ran).containsExactly("sibling", "after");
    }
}
