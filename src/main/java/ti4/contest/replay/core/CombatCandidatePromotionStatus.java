package ti4.contest.replay.core;

/**
 * Tracks whether a resolved candidate is still eligible to become a public replay contest.
 */
public enum CombatCandidatePromotionStatus {
    /** Resolved candidate has not yet been promoted or expired. */
    PENDING,
    /** Candidate has been promoted into a public replay contest. */
    PROMOTED,
    /** Candidate aged out or was cancelled before promotion. */
    EXPIRED
}
