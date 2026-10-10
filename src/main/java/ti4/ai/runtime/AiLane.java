package ti4.ai.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Getter;
import ti4.ai.fallback.DelegatedPress;
import ti4.game.Player;

final class AiLane {

    private static final long FAILURE_WINDOW_MILLIS = 600_000L;

    @Getter
    private final String gameName;

    private final AtomicBoolean running = new AtomicBoolean();
    private final Queue<DelegatedPress> delegatedPresses = new ConcurrentLinkedQueue<>();
    private final Map<String, AiSeatState> seats = new ConcurrentHashMap<>();
    private final Deque<Long> failures = new ArrayDeque<>();

    private final AtomicLong dueAt = new AtomicLong();
    private final Map<String, Integer> refereeAttempts = new ConcurrentHashMap<>();
    private volatile long lastSeenModified;
    private volatile int lastUndoIndex = -1;
    private volatile long lastWatchdog;
    private long lastActionAt;
    private String lastActorId = "";
    private boolean gameOverAnnounced;
    private int delegationRound = -1;
    private int delegationsThisRound;

    AiLane(String gameName) {
        this.gameName = gameName;
    }

    void markDirty(long at) {
        dueAt.accumulateAndGet(at, (current, wanted) -> current == 0 ? wanted : Math.min(current, wanted));
    }

    boolean isDue(long now) {
        long due = dueAt.get();
        return due != 0 && due <= now;
    }

    boolean tryStart() {
        if (!running.compareAndSet(false, true)) return false;
        dueAt.set(0);
        return true;
    }

    boolean observeUndoIndex(int undoIndex) {
        int previous = lastUndoIndex;
        lastUndoIndex = undoIndex;
        return previous >= 0 && undoIndex < previous;
    }

    void forgetAfterUndo() {
        seats.values().forEach(AiSeatState::forgetAfterUndo);
        refereeAttempts.clear();
    }

    boolean refereeMayPress(String pressKey, int stateFingerprint) {
        Integer previous = refereeAttempts.get(pressKey);
        return previous == null || previous != stateFingerprint;
    }

    void recordRefereePress(String pressKey, int stateFingerprint) {
        if (refereeAttempts.size() > 200) refereeAttempts.clear();
        refereeAttempts.put(pressKey, stateFingerprint);
    }

    void finish() {
        running.set(false);
    }

    boolean observeModified(long modified) {
        if (modified == lastSeenModified) return false;
        lastSeenModified = modified;
        return true;
    }

    boolean watchdogDue(long now, long period) {
        if (now - lastWatchdog < period) return false;
        lastWatchdog = now;
        return true;
    }

    void addDelegatedPress(DelegatedPress press) {
        delegatedPresses.add(press);
    }

    DelegatedPress pollDelegatedPress() {
        return delegatedPresses.poll();
    }

    void requestDelegation(String seatId, boolean explicit) {
        seat(seatId).requestDelegation(explicit);
    }

    AiSeatState seat(String seatId) {
        return seats.computeIfAbsent(seatId, ignored -> new AiSeatState());
    }

    void retainSeats(List<Player> active) {
        Set<String> ids = active.stream().map(Player::getUserID).collect(Collectors.toSet());
        seats.keySet().removeIf(id -> !ids.contains(id));
    }

    List<Player> rotation(List<Player> active) {
        List<Player> ordered = new ArrayList<>(active);
        int last = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getUserID().equals(lastActorId)) last = i;
        }
        List<Player> rotated = new ArrayList<>(ordered.subList(last + 1, ordered.size()));
        rotated.addAll(ordered.subList(0, last + 1));
        return rotated;
    }

    void recordAction(String seatId, long now) {
        lastActionAt = now;
        lastActorId = seatId;
    }

    long lastActionAt() {
        return lastActionAt;
    }

    synchronized void recordFailure(long now) {
        failures.addLast(now);
        pruneFailures(now);
    }

    synchronized int recentFailures(long now) {
        pruneFailures(now);
        return failures.size();
    }

    private void pruneFailures(long now) {
        while (!failures.isEmpty() && now - failures.peekFirst() > FAILURE_WINDOW_MILLIS) failures.removeFirst();
    }

    boolean recordGameDelegation(int round, int maxPerRound) {
        if (round != delegationRound) {
            delegationRound = round;
            delegationsThisRound = 0;
        }
        if (delegationsThisRound >= maxPerRound) return false;
        delegationsThisRound++;
        return true;
    }

    boolean markGameOverAnnounced() {
        if (gameOverAnnounced) return false;
        gameOverAnnounced = true;
        return true;
    }
}
