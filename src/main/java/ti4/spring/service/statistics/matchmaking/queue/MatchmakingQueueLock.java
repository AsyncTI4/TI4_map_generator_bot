package ti4.spring.service.statistics.matchmaking.queue;

import java.util.concurrent.locks.ReentrantLock;
import lombok.experimental.UtilityClass;

/**
 * Serializes the load-match-delete critical sections of {@link MatchmakerService#processQueue} and
 * {@link PlayerSearchService#searchAndAdd} so a queued party can never be matched into two games at once.
 *
 * <p>Never acquire this lock from a method that is itself running inside a transaction: the transaction would hold
 * a pooled Postgres connection, and its row locks, for as long as it waits on the lock. Never hold it across
 * Discord {@code .queue()} dispatch.
 */
@UtilityClass
class MatchmakingQueueLock {

    static final ReentrantLock LOCK = new ReentrantLock();
}
