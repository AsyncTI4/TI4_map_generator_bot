package ti4.contest.replay.core;

/**
 * Lifecycle state for a tracked combat candidate before it is promoted or discarded.
 */
public enum CombatCandidateStatus {
    /** Combat is still active and accepting mirrored events. */
    TRACKING,
    /** Combat appears complete, but remains open briefly to capture sequential hit assignment. */
    PENDING_RESOLUTION,
    /** Combat ended and can be considered for promotion. */
    RESOLVED,
    /** Combat was abandoned, superseded, or otherwise closed before normal resolution. */
    CANCELLED
}
