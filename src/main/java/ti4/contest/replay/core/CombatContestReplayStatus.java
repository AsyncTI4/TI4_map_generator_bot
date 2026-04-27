package ti4.contest.replay.core;

/**
 * Lifecycle state for a promoted contest as its recorded events are replayed to Discord.
 */
public enum CombatContestReplayStatus {
    PENDING,
    REPLAYING,
    COMPLETED,
    FAILED
}
