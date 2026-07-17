package ti4.contest.replay.core;

/**
 * High-level categories for ordered events recorded against a replay candidate.
 */
public enum CombatCandidateEventType {
    /** First recorded event for a candidate; not replay-posted because promotion already posts the opener. */
    START,
    /** Structured combat roll payload. */
    ROLL,
    /** Structured tile state after hit assignment. */
    HIT_ASSIGN,
    /** Generic replay message or structured non-roll interaction payload. */
    INFO,
    /** Terminal contest resolution event. */
    RESOLVED,
    /** Terminal cancellation event for candidates that never resolve normally. */
    CANCELLED
}
