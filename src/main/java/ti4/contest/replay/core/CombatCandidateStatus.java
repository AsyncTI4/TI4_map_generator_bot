package ti4.contest.replay.core;

/**
 * Lifecycle state for a tracked combat candidate before it is promoted or discarded.
 */
public enum CombatCandidateStatus {
    TRACKING,
    RESOLVED,
    CANCELLED
}
