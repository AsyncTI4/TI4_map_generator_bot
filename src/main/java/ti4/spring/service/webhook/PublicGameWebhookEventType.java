package ti4.spring.service.webhook;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import ti4.service.webhook.GameWebhookEventType;

public enum PublicGameWebhookEventType {
    TURN_CHANGED("A new player's turn has started", GameWebhookEventType.ACTIVE_PLAYER_CHANGED),
    PHASE_CHANGED("The game entered a new phase (strategy, action, agenda)", GameWebhookEventType.PHASE_CHANGED),
    GAME_ENDED("The game ended", GameWebhookEventType.GAME_ENDED);

    private final String description;
    private final GameWebhookEventType internalType;

    PublicGameWebhookEventType(String description, GameWebhookEventType internalType) {
        this.description = description;
        this.internalType = internalType;
    }

    public String description() {
        return description;
    }

    public GameWebhookEventType internalType() {
        return internalType;
    }

    public static Optional<PublicGameWebhookEventType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(PublicGameWebhookEventType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<PublicGameWebhookEventType> fromInternal(GameWebhookEventType eventType) {
        return Arrays.stream(values())
                .filter(type -> type.internalType == eventType)
                .findFirst();
    }
}
