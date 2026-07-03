package ti4.service.webhook;

public enum GameWebhookEventType {
    ACTIVE_PLAYER_CHANGED("active_player_changed"),
    PHASE_CHANGED("phase_changed"),
    AGENDA_VOTING_STARTED("agenda_voting_started"),
    AGENDA_RESOLVED("agenda_resolved"),
    PLAYER_PASSED("player_passed"),
    GAME_ENDED("game_ended");

    private final String value;

    GameWebhookEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
