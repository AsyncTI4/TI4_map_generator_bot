package ti4.spring.service.statistics.matchmaking.queue;

import java.util.concurrent.locks.ReentrantLock;
import lombok.experimental.UtilityClass;

/**
 * Serializes the load-match-delete critical sections of {@link MatchmakerService#processQueue} and
 * {@link PlayerSearchService#searchAndAdd} so a queued party can never be matched into two games at once.
 * This replaces the accidental serialization those methods previously got from whole-method transactions
 * on the single-connection pool.
 *
 * <p>Never acquire this lock from a method that is itself running inside a transaction (with a pool size
 * of one, a transaction waiting on the lock while the lock holder needs the connection would stall until
 * the pool timeout), and never hold it across Discord {@code .queue()} dispatch.
 */
@UtilityClass
class MatchmakingQueueLock {

    static final ReentrantLock LOCK = new ReentrantLock();
}
