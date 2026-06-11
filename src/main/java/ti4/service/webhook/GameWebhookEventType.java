package ti4.service.webhook;

import java.util.Map;

public enum GameWebhookEventType {
    TURN_CHANGED(
            "A new player's turn has started.",
            "Emitted after the bot updates the active player in StartTurnService.turnStart.",
            Map.of(
                    "gameName", "string",
                    "eventType", "string",
                    "phaseOfGame", "string",
                    "round", "integer",
                    "activePlayerId", "string",
                    "activeFaction", "string",
                    "timestamp", "date-time")),
    PHASE_CHANGED(
            "The game entered a new core phase.",
            "Emitted after the bot updates the game phase for strategy, action, status, or agenda.",
            Map.of(
                    "gameName", "string",
                    "eventType", "string",
                    "previousPhaseOfGame", "string|null",
                    "phaseOfGame", "string",
                    "round", "integer",
                    "activePlayerId", "string|null",
                    "activeFaction", "string|null",
                    "timestamp", "date-time"));

    private final String description;
    private final String trigger;
    private final Map<String, String> payloadFields;

    GameWebhookEventType(String description, String trigger, Map<String, String> payloadFields) {
        this.description = description;
        this.trigger = trigger;
        this.payloadFields = payloadFields;
    }

    public String getDescription() {
        return description;
    }

    public String getTrigger() {
        return trigger;
    }

    public Map<String, String> getPayloadFields() {
        return payloadFields;
    }
}
