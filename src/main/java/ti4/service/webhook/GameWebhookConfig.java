package ti4.service.webhook;

import java.net.URI;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

@UtilityClass
public class GameWebhookConfig {
    static final String WEBHOOK_URL_KEY = "gameWebhookUrl";
    static final String WEBHOOK_ENABLED_KEY = "gameWebhookEnabled";
    static final String WEBHOOK_ALLOW_FOW_KEY = "gameWebhookAllowFow";

    public static Optional<String> getWebhookUrl(Game game) {
        String webhookUrl = StringUtils.trimToEmpty(game.getStoredValue(WEBHOOK_URL_KEY));
        if (webhookUrl.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(webhookUrl);
    }

    public static boolean isWebhookEnabled(Game game) {
        String enabled = game.getStoredValue(WEBHOOK_ENABLED_KEY);
        return enabled.isEmpty() || Boolean.parseBoolean(enabled);
    }

    public static boolean isFowAllowed(Game game) {
        return Boolean.parseBoolean(game.getStoredValue(WEBHOOK_ALLOW_FOW_KEY));
    }

    public static void setWebhookUrl(Game game, String webhookUrl) {
        String value = StringUtils.trimToEmpty(webhookUrl);
        if (value.isEmpty()) {
            game.removeStoredValue(WEBHOOK_URL_KEY);
            return;
        }
        game.setStoredValue(WEBHOOK_URL_KEY, value);
    }

    public static void setWebhookEnabled(Game game, boolean enabled) {
        game.setStoredValue(WEBHOOK_ENABLED_KEY, Boolean.toString(enabled));
    }

    public static void setFowAllowed(Game game, boolean allowFow) {
        game.setStoredValue(WEBHOOK_ALLOW_FOW_KEY, Boolean.toString(allowFow));
    }

    public static void clearWebhookConfig(Game game) {
        game.removeStoredValue(WEBHOOK_URL_KEY);
        game.removeStoredValue(WEBHOOK_ENABLED_KEY);
        game.removeStoredValue(WEBHOOK_ALLOW_FOW_KEY);
    }

    public static boolean isWebhookUrlValid(String webhookUrl) {
        try {
            URI uri = URI.create(webhookUrl);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
