package ti4.contest.replay.service;

public record CombatReplayInteractionResult(boolean accepted, String message) {
    public static CombatReplayInteractionResult accepted(String message) {
        return new CombatReplayInteractionResult(true, message);
    }

    public static CombatReplayInteractionResult rejected(String message) {
        return new CombatReplayInteractionResult(false, message);
    }
}
