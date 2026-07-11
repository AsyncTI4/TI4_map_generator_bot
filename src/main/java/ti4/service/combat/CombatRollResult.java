package ti4.service.combat;

import ti4.contest.replay.core.CombatRollPayload;

public record CombatRollResult(
        CombatRollStatus status,
        String message,
        int totalHits,
        boolean whiff,
        boolean slam,
        CombatRollPayload payload) {

    public static CombatRollResult stopped(CombatRollStatus status) {
        if (status == CombatRollStatus.COMPLETED) {
            throw new IllegalArgumentException("A stopped combat roll cannot have COMPLETED status");
        }
        return new CombatRollResult(status, "", 0, false, false, null);
    }

    public CombatRollResult withPublishedResult(String publishedMessage, int publishedHits, CombatRollPayload payload) {
        return new CombatRollResult(status, publishedMessage, publishedHits, whiff, slam, payload);
    }
}
