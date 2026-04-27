package ti4.contest.replay.core;

/**
 * Side-bet-relevant combat interactions that are tracked independently from replay rendering.
 */
public enum CombatReplayTrackedEvent {
    /** No side-bet-relevant interaction was identified. */
    NONE,
    /** Current tracked action-card play side bet. */
    MORALE_BOOST,
    /** Current tracked action-card play side bet. */
    SHIELDS_HOLDING,
    /** Combat-ending action card that makes the candidate ineligible for promotion. */
    ROUT
}
