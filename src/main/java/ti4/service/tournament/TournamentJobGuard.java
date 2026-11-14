package ti4.service.tournament;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import ti4.executors.ExecutorServiceManager;

public final class TournamentJobGuard<K> {

    static final long UNSTARTED_TIMEOUT_MILLIS = 60_000;
    private static final AtomicLong TOKENS = new AtomicLong();

    private final Map<K, Claim> claims = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public TournamentJobGuard() {
        this(System::currentTimeMillis);
    }

    TournamentJobGuard(LongSupplier clock) {
        this.clock = clock;
    }

    private record Claim(long token, long since, boolean started) {

        boolean isLive(long now) {
            return started || now - since < UNSTARTED_TIMEOUT_MILLIS;
        }

        Claim asStarted() {
            return new Claim(token, since, true);
        }
    }

    public interface Submitter {
        void submit(Runnable task);
    }

    public boolean isRunning(K key) {
        Claim claim = claims.get(key);
        return claim != null && claim.isLive(clock.getAsLong());
    }

    public boolean submit(K key, String taskName, int warnAfterSeconds, Runnable task) {
        return submit(key, task, wrapped -> ExecutorServiceManager.runAsync(taskName, warnAfterSeconds, wrapped));
    }

    boolean submit(K key, Runnable task, Submitter submitter) {
        long now = clock.getAsLong();
        Claim mine = new Claim(TOKENS.incrementAndGet(), now, false);
        Claim current = claims.merge(key, mine, (existing, fresh) -> existing.isLive(now) ? existing : fresh);
        if (current != mine) return false;
        submitter.submit(() -> {
            Claim started = mine.asStarted();
            if (!claims.replace(key, mine, started)) return;
            try {
                task.run();
            } finally {
                claims.remove(key, started);
            }
        });
        return true;
    }
}
