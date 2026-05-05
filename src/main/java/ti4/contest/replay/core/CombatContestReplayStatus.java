package ti4.contest.replay.core;

/**
 * Lifecycle state for a promoted contest as its recorded events are replayed to Discord.
 */
public enum CombatContestReplayStatus {
    /** Contest has been selected for private preview but not publicly posted. */
    PREVIEW,
    /** Contest has been publicly posted but replay ticks have not started. */
    PENDING,
    /** Replay events are actively being posted or retried. */
    REPLAYING,
    /** Replay and leaderboard posting are complete. */
    COMPLETED
}
