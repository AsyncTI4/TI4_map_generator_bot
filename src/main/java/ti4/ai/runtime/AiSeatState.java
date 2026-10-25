package ti4.ai.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import ti4.ai.brain.AiMemory;

final class AiSeatState {

    enum DelegationRequest {
        NONE,
        GAME_WIDE,
        EXPLICIT
    }

    enum FallbackVerdict {
        CHOOSE,
        STOP_AND_ANNOUNCE,
        STOPPED
    }

    private record Decision(String promptKey, String text) {}

    private static final int PRESSED_MEMORY = 200;
    private static final int DECISION_MEMORY = 60;
    private static final long HOUR_MILLIS = 3_600_000L;

    private final Map<String, Integer> attempts = new HashMap<>();
    private final Deque<Long> actionTimes = new ArrayDeque<>();
    private final Deque<String> pressedOrder = new ArrayDeque<>();
    private final Set<String> pressedKeys = new HashSet<>();
    private final Set<String> delegatedPrompts = new HashSet<>();
    private final Deque<Decision> decisions = new ArrayDeque<>();
    private AiMemory memory = new AiMemory();

    private volatile DelegationRequest delegationRequest = DelegationRequest.NONE;
    private long stallSince;
    private String stallReasons = "";
    private boolean stuckAnnounced;
    private int delegationRound = -1;
    private int delegationsThisRound;
    private String fallbackKey = "";
    private int repeatedFallbacks;
    private long thinkingHeldAt;
    private int thinkingHeldFingerprint;
    private int abortLoggedRound = -1;

    synchronized AiSeatState copy() {
        AiSeatState copy = new AiSeatState();
        copy.attempts.putAll(attempts);
        copy.actionTimes.addAll(actionTimes);
        copy.pressedOrder.addAll(pressedOrder);
        copy.pressedKeys.addAll(pressedKeys);
        copy.delegatedPrompts.addAll(delegatedPrompts);
        copy.decisions.addAll(decisions);
        copy.memory = new AiMemory();
        copy.memory.restore(memory.snapshot());
        copy.delegationRequest = delegationRequest;
        copy.stallSince = stallSince;
        copy.stallReasons = stallReasons;
        copy.stuckAnnounced = stuckAnnounced;
        copy.delegationRound = delegationRound;
        copy.delegationsThisRound = delegationsThisRound;
        copy.fallbackKey = fallbackKey;
        copy.repeatedFallbacks = repeatedFallbacks;
        copy.thinkingHeldAt = thinkingHeldAt;
        copy.thinkingHeldFingerprint = thinkingHeldFingerprint;
        copy.abortLoggedRound = abortLoggedRound;
        return copy;
    }

    void requestDelegation(boolean explicit) {
        DelegationRequest requested = explicit ? DelegationRequest.EXPLICIT : DelegationRequest.GAME_WIDE;
        if (delegationRequest != DelegationRequest.EXPLICIT) delegationRequest = requested;
    }

    boolean hasDelegationRequest() {
        return delegationRequest != DelegationRequest.NONE;
    }

    DelegationRequest consumeDelegationRequest() {
        DelegationRequest requested = delegationRequest;
        delegationRequest = DelegationRequest.NONE;
        return requested;
    }

    int recordAttempt(String key) {
        if (attempts.size() > PRESSED_MEMORY) attempts.clear();
        return attempts.merge(key, 1, Integer::sum);
    }

    void recordDecision(String promptKey, String text) {
        decisions.addLast(new Decision(promptKey, text));
        while (decisions.size() > DECISION_MEMORY) decisions.removeFirst();
    }

    List<String> recentDecisionsOn(String promptKey, int limit) {
        List<String> found = new ArrayList<>();
        Iterator<Decision> newestFirst = decisions.descendingIterator();
        while (newestFirst.hasNext() && found.size() < limit) {
            Decision decision = newestFirst.next();
            if (decision.promptKey().equals(promptKey)) found.add(decision.text());
        }
        Collections.reverse(found);
        return found;
    }

    int attemptsFor(String key) {
        return attempts.getOrDefault(key, 0);
    }

    void recordPressed(String pressKey, long now) {
        actionTimes.addLast(now);
        if (pressedKeys.add(pressKey)) pressedOrder.addLast(pressKey);
        while (pressedOrder.size() > PRESSED_MEMORY) pressedKeys.remove(pressedOrder.removeFirst());
        stallSince = 0;
        stuckAnnounced = false;
    }

    void forgetAfterUndo() {
        memory = new AiMemory();
        pressedKeys.clear();
        pressedOrder.clear();
        decisions.clear();
        thinkingHeldAt = 0;
        attempts.clear();
        delegatedPrompts.clear();
        stallSince = 0;
        stuckAnnounced = false;
        clearFallbacks();
    }

    AiMemory memory() {
        return memory;
    }

    Set<String> pressedKeys() {
        return Set.copyOf(pressedKeys);
    }

    boolean hasPressed(String pressKey) {
        return pressedKeys.contains(pressKey);
    }

    int actionsInLastHour(long now) {
        while (!actionTimes.isEmpty() && now - actionTimes.peekFirst() > HOUR_MILLIS) actionTimes.removeFirst();
        return actionTimes.size();
    }

    long stallAge(String reasons, long now) {
        if (reasons.isEmpty()) {
            stallSince = 0;
            stallReasons = "";
            stuckAnnounced = false;
            return 0;
        }
        if (stallSince == 0 || !reasons.equals(stallReasons)) {
            stallSince = now;
            stallReasons = reasons;
            stuckAnnounced = false;
        }
        return now - stallSince;
    }

    void restartStall(long now) {
        if (stallSince != 0) stallSince = now;
    }

    boolean markStuckAnnounced() {
        if (stuckAnnounced) return false;
        stuckAnnounced = true;
        return true;
    }

    boolean wasDelegated(String promptKey) {
        return delegatedPrompts.contains(promptKey);
    }

    boolean canDelegate(int round, int maxPerRound) {
        if (round != delegationRound) {
            delegationRound = round;
            delegationsThisRound = 0;
        }
        return delegationsThisRound < maxPerRound;
    }

    void recordDelegation(String promptKey) {
        delegationsThisRound++;
        delegatedPrompts.add(promptKey);
    }

    FallbackVerdict recordFallback(String loopKey, int limit) {
        if (!loopKey.equals(fallbackKey)) {
            fallbackKey = loopKey;
            repeatedFallbacks = 0;
        }
        repeatedFallbacks = Math.min(repeatedFallbacks + 1, limit + 2);
        if (repeatedFallbacks <= limit) return FallbackVerdict.CHOOSE;
        return repeatedFallbacks == limit + 1 ? FallbackVerdict.STOP_AND_ANNOUNCE : FallbackVerdict.STOPPED;
    }

    boolean firstAbortInRound(int round) {
        if (abortLoggedRound == round) return false;
        abortLoggedRound = round;
        return true;
    }

    void holdThinking(long now, int fingerprint) {
        thinkingHeldAt = now;
        thinkingHeldFingerprint = fingerprint;
    }

    boolean thinkingHeld(long now, long retryMillis, IntSupplier fingerprint) {
        if (thinkingHeldAt == 0 || now - thinkingHeldAt >= retryMillis) return false;
        return fingerprint.getAsInt() == thinkingHeldFingerprint;
    }

    void clearFallbacks() {
        fallbackKey = "";
        repeatedFallbacks = 0;
    }
}
