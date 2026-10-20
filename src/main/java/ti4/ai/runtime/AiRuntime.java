package ti4.ai.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import ti4.AsyncTI4DiscordBot;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.fallback.DelegatedPress;
import ti4.cron.CronManager;
import ti4.discord.JdaService;
import ti4.executors.CircuitBreaker;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class AiRuntime {

    private static final int WORKER_THREADS = 2;
    private static final long INITIAL_POLL_DELAY_SECONDS = 60;
    private static final Map<String, AiLane> LANES = new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean DISCOVERED = new AtomicBoolean();
    private static final long DISCOVERY_RETRY_MILLIS = 60_000L;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static volatile ExecutorService workers;
    private static volatile long lastDiscoveryAttempt;

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        workers = Executors.newFixedThreadPool(WORKER_THREADS, daemonThreads());
        CronManager.schedulePeriodically(
                AiRuntime.class,
                AiRuntime::poll,
                INITIAL_POLL_DELAY_SECONDS,
                AiSettings.POLL_PERIOD.toSeconds(),
                TimeUnit.SECONDS);
    }

    public static void shutdown() {
        ExecutorService current = workers;
        if (current == null) return;
        current.shutdown();
        try {
            if (!current.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) current.shutdownNow();
        } catch (InterruptedException e) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void register(String gameName) {
        lane(gameName).markDirty(System.currentTimeMillis() + AiSettings.DEBOUNCE.toMillis());
    }

    public static void forget(String gameName) {
        LANES.remove(gameName);
    }

    static void forget(AiLane lane) {
        LANES.remove(lane.getGameName(), lane);
    }

    public static boolean isTracked(String gameName) {
        return LANES.containsKey(gameName);
    }

    public static void enqueueDelegatedPress(DelegatedPress press) {
        AiLane lane = lane(press.gameName());
        lane.addDelegatedPress(press);
        lane.markDirty(System.currentTimeMillis());
    }

    public static void requestDelegation(String gameName, String seatId, boolean explicit) {
        AiLane lane = lane(gameName);
        lane.requestDelegation(seatId, explicit);
        lane.markDirty(System.currentTimeMillis());
    }

    public static boolean isCoolingDown(String gameName) {
        AiLane lane = LANES.get(gameName);
        return lane != null && AiTickRunner.isCoolingDown(lane, System.currentTimeMillis());
    }

    static void poll() {
        if (!isProcessReady() || workers == null) return;
        long now = System.currentTimeMillis();
        if (!DISCOVERED.get() && now - lastDiscoveryAttempt >= DISCOVERY_RETRY_MILLIS) {
            lastDiscoveryAttempt = now;
            workers.submit(AiRuntime::discover);
        }
        for (AiLane lane : LANES.values()) {
            ManagedGame managed = managedGame(lane.getGameName());
            if (managed == null || managed.isHasEnded()) {
                LANES.remove(lane.getGameName());
                continue;
            }
            if (lane.observeModified(managed.getLastModifiedDate())) {
                lane.markDirty(now + AiSettings.DEBOUNCE.toMillis());
            }
            boolean dormant = now - managed.getLastModifiedDate() > AiSettings.DORMANT_AFTER.toMillis();
            if (!dormant && lane.watchdogDue(now, AiSettings.WATCHDOG_PERIOD.toMillis())) lane.markDirty(now);
            if (lane.isDue(now) && lane.tryStart()) workers.submit(() -> run(lane));
        }
    }

    static boolean isProcessReady() {
        return JdaService.jda != null
                && JdaService.isReadyToReceiveCommands()
                && ActiveLeaseService.shouldHandleCurrentProcessInteraction()
                && !AsyncTI4DiscordBot.isUnstable()
                && !CircuitBreaker.isOpen();
    }

    static boolean mayMutate() {
        return isProcessReady() && ActiveLeaseService.shouldCurrentProcessRunScheduledWork();
    }

    private static void run(AiLane lane) {
        try {
            AiTickRunner.tick(lane);
        } catch (Throwable t) {
            BotLogger.error("AI tick failed in " + lane.getGameName(), t);
        } finally {
            lane.finish();
        }
    }

    private static void discover() {
        try {
            for (ManagedGame managed : GameManager.getManagedGames()) {
                if (!managed.isHasEnded() && AiSeats.hasAiSeat(managed)) register(managed.getName());
            }
            DISCOVERED.set(true);
        } catch (RuntimeException e) {
            BotLogger.info("AI players could not look for AI games yet and will retry: " + e.getMessage());
        }
    }

    private static ManagedGame managedGame(String gameName) {
        return GameManager.isValid(gameName) ? GameManager.getManagedGame(gameName) : null;
    }

    private static AiLane lane(String gameName) {
        return LANES.computeIfAbsent(gameName, AiLane::new);
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "ai-player-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
