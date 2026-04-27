package ti4.contest.replay.core;

/**
 * Tracks whether a resolved candidate is still eligible to become a public replay contest.
 */
public enum CombatCandidatePromotionStatus {
    PENDING,
    PROMOTED,
    EXPIRED
}
