package ti4.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import ti4.cron.CronManager;
import ti4.discord.JdaService.ShutdownStep;
import ti4.executors.ExecutorServiceManager;
import ti4.game.persistence.GameDatabaseSyncPipeline;
import ti4.image.map.MapRenderPipeline;
import ti4.service.draft.SliceGenerationPipeline;
import ti4.service.statistics.StatisticsPipeline;
import ti4.spring.websocket.GameWebStatePipeline;

class JdaServiceShutdownStepsTest {

    private final Map<String, Throwable> failures = new ConcurrentHashMap<>();
    private final BiConsumer<String, Throwable> recordFailure = failures::put;

    @Test
    void aFailingStepDoesNotSkipTheStepsAfterIt() {
        // Before, one exception (e.g. a BotLogger webhook RejectedExecutionException once CronManager was shut
        // down) skipped every later step, including draining the pipelines and releasing the active lease.
        List<String> ran = new CopyOnWriteArrayList<>();

        JdaService.runShutdownPhases(
                List.of(
                        List.of(new ShutdownStep("first", () -> ran.add("first"))),
                        List.of(new ShutdownStep("failing", () -> {
                            throw new RejectedExecutionException("simulated");
                        })),
                        List.of(new ShutdownStep("release active lease", () -> ran.add("release active lease")))),
                recordFailure);

        assertThat(ran).containsExactly("first", "release active lease");
        assertThat(failures).containsOnlyKeys("failing");
    }

    @Test
    void anErrorIsReportedLikeAnExceptionAndDoesNotSkipLaterSteps() {
        List<String> ran = new CopyOnWriteArrayList<>();

        JdaService.runShutdownPhases(
                List.of(
                        List.of(new ShutdownStep("erroring", () -> {
                            throw new StackOverflowError("simulated");
                        })),
                        List.of(new ShutdownStep("after", () -> ran.add("after")))),
                recordFailure);

        assertThat(ran).containsExactly("after");
        assertThat(failures.get("erroring")).isInstanceOf(StackOverflowError.class);
    }

    @Test
    void stepsInOnePhaseRunAtTheSameTime() {
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

        JdaService.runShutdownPhases(
                List.of(List.of(new ShutdownStep("a", awaitOther), new ShutdownStep("b", awaitOther))), recordFailure);

        assertThat(sawTheOtherStart).containsExactly(true, true);
    }

    @Test
    void theNextPhaseWaitsForEveryStepInThePreviousOne() {
        List<String> ran = new CopyOnWriteArrayList<>();
        Runnable slow = () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            ran.add("slow producer");
        };

        JdaService.runShutdownPhases(
                List.of(
                        List.of(
                                new ShutdownStep("slow", slow),
                                new ShutdownStep("fast", () -> ran.add("fast producer"))),
                        List.of(new ShutdownStep("consumer", () -> ran.add("consumer")))),
                recordFailure);

        assertThat(ran).containsExactlyInAnyOrder("slow producer", "fast producer", "consumer");
        assertThat(ran.getLast()).isEqualTo("consumer");
    }

    @Test
    void aFailingStepInAPhaseIsReportedAndDoesNotStopItsSiblings() {
        List<String> ran = new CopyOnWriteArrayList<>();

        JdaService.runShutdownPhases(
                List.of(
                        List.of(
                                new ShutdownStep("failing", () -> {
                                    throw new RejectedExecutionException("simulated");
                                }),
                                new ShutdownStep("sibling", () -> ran.add("sibling"))),
                        List.of(new ShutdownStep("after", () -> ran.add("after")))),
                recordFailure);

        assertThat(ran).containsExactly("sibling", "after");
        assertThat(failures).containsOnlyKeys("failing");
    }

    @Test
    void anInterruptDuringAPhaseStillWaitsForTheWholePhase() throws InterruptedException {
        // An interrupted shutdown thread must not start the next phase while the previous one is still draining.
        List<String> ran = new CopyOnWriteArrayList<>();
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        Runnable slow = () -> {
            slowStarted.countDown();
            try {
                releaseSlow.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            ran.add("slow");
        };
        List<Boolean> interruptFlagAfterPhases = new CopyOnWriteArrayList<>();

        Thread shutdownThread = Thread.ofVirtual().start(() -> {
            JdaService.runShutdownPhases(
                    List.of(
                            List.of(new ShutdownStep("slow", slow), new ShutdownStep("other", () -> {})),
                            List.of(new ShutdownStep("next phase", () -> ran.add("next phase")))),
                    recordFailure);
            interruptFlagAfterPhases.add(Thread.currentThread().isInterrupted());
        });
        assertThat(slowStarted.await(5, TimeUnit.SECONDS)).isTrue();

        shutdownThread.interrupt();
        Thread.sleep(100);
        releaseSlow.countDown();
        shutdownThread.join(5000);

        assertThat(ran).containsExactly("slow", "next phase");
        assertThat(interruptFlagAfterPhases).containsExactly(true);
    }

    @Test
    void executorsCloseOnlyAfterEverythingThatSubmitsToThem() {
        List<List<String>> phases = JdaService.shutdownPhases().stream()
                .map(phase -> phase.stream().map(ShutdownStep::name).toList())
                .toList();

        int workers = phaseOf(phases, ExecutorServiceManager.class.getSimpleName());
        int crons = phaseOf(phases, CronManager.class.getSimpleName());
        int activeHoursFlush = phaseOf(phases, JdaService.FLUSH_USER_ACTIVE_HOURS);
        int lease = phaseOf(phases, JdaService.RELEASE_ACTIVE_LEASE);

        // Workers and crons may drain together: workers do schedule on the cron scheduler (circuit breaker, logs),
        // but CronManager.scheduleOnce refuses quietly once it is shut down (see CronManagerTest).
        // Button presses record active hours on worker threads, so the flush must come after they finish.
        assertThat(activeHoursFlush).isGreaterThan(workers);
        // Workers queue renders, slice generation and statistics jobs.
        for (Class<?> pipeline :
                List.of(SliceGenerationPipeline.class, MapRenderPipeline.class, StatisticsPipeline.class)) {
            assertThat(phaseOf(phases, pipeline.getSimpleName()))
                    .as(pipeline.getSimpleName())
                    .isGreaterThan(workers);
        }
        // Everything above can save a game, which queues onto the persistence pipelines.
        for (Class<?> pipeline : List.of(GameWebStatePipeline.class, GameDatabaseSyncPipeline.class)) {
            int persistence = phaseOf(phases, pipeline.getSimpleName());
            assertThat(persistence)
                    .as(pipeline.getSimpleName())
                    .isGreaterThan(workers)
                    .isGreaterThan(crons)
                    .isGreaterThan(phaseOf(phases, SliceGenerationPipeline.class.getSimpleName()))
                    .isGreaterThan(phaseOf(phases, MapRenderPipeline.class.getSimpleName()))
                    .isLessThan(lease);
        }
    }

    private static int phaseOf(List<List<String>> phases, String stepName) {
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).contains(stepName)) {
                return i;
            }
        }
        throw new AssertionError("No shutdown step named " + stepName + " in " + phases);
    }
}
