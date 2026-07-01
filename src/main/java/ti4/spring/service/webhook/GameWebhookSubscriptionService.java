package ti4.spring.service.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.service.webhook.GameWebhookEventType;

@Service
public class GameWebhookSubscriptionService {

    public enum SubscribeResult {
        OK,
        UNAUTHORIZED,
        UNKNOWN_GAME,
        FORBIDDEN_GAME,
        UNSUPPORTED_EVENT_TYPE
    }

    private final WebhookUserRepository webhookUserRepository;
    private final GameWebhookRepository gameWebhookRepository;

    public GameWebhookSubscriptionService(
            WebhookUserRepository webhookUserRepository, GameWebhookRepository gameWebhookRepository) {
        this.webhookUserRepository = webhookUserRepository;
        this.gameWebhookRepository = gameWebhookRepository;
    }

    @Transactional
    public SubscribeResult subscribe(String gameName, String apiKey, List<String> eventTypes) {
        WebhookUserEntity user = getActiveUser(apiKey);
        if (user == null) return SubscribeResult.UNAUTHORIZED;
        if (!GameManager.isValid(gameName)) return SubscribeResult.UNKNOWN_GAME;
        if (!canSubscribe(gameName)) return SubscribeResult.FORBIDDEN_GAME;
        if (!areSupported(eventTypes)) return SubscribeResult.UNSUPPORTED_EVENT_TYPE;

        String eventTypesCsv = normalizedEventTypes(eventTypes).collect(Collectors.joining(","));
        GameWebhookEntity entity = gameWebhookRepository
                .findByGameNameAndWebhookUserId(gameName, user.getId())
                .orElseGet(GameWebhookEntity::new);
        entity.setGameName(gameName);
        entity.setWebhookUserId(user.getId());
        entity.setEventTypesCsv(eventTypesCsv);
        gameWebhookRepository.save(entity);
        return SubscribeResult.OK;
    }

    @Transactional
    public SubscribeResult delete(String gameName, String apiKey) {
        WebhookUserEntity user = getActiveUser(apiKey);
        if (user == null) return SubscribeResult.UNAUTHORIZED;
        if (!GameManager.isValid(gameName)) return SubscribeResult.UNKNOWN_GAME;
        if (!canSubscribe(gameName)) return SubscribeResult.FORBIDDEN_GAME;
        gameWebhookRepository.deleteByGameNameAndWebhookUserId(gameName, user.getId());
        return SubscribeResult.OK;
    }

    public List<WebhookUserEntity> getSubscribers(String gameName, GameWebhookEventType eventType) {
        if (!canSubscribe(gameName)) return List.of();
        return gameWebhookRepository.findByGameName(gameName).stream()
                .filter(subscription -> includes(subscription, eventType))
                .map(subscription -> webhookUserRepository.findById(subscription.getWebhookUserId()))
                .flatMap(Optional::stream)
                .filter(WebhookUserEntity::isActive)
                .toList();
    }

    private WebhookUserEntity getActiveUser(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return null;
        return webhookUserRepository.findByApiKeyHashAndActiveTrue(sha256(apiKey)).orElse(null);
    }

    private static boolean canSubscribe(String gameName) {
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        return managedGame != null && !managedGame.isFowMode();
    }

    private static boolean areSupported(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) return false;
        Set<String> supported = Arrays.stream(GameWebhookEventType.values()).map(Enum::name).collect(Collectors.toSet());
        return eventTypes.stream()
                .map(GameWebhookSubscriptionService::normalizeEventType)
                .allMatch(supported::contains);
    }

    private static Stream<String> normalizedEventTypes(List<String> eventTypes) {
        return eventTypes.stream()
                .map(GameWebhookSubscriptionService::normalizeEventType)
                .distinct()
                .sorted();
    }

    private static String normalizeEventType(String eventType) {
        return eventType == null ? null : eventType.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean includes(GameWebhookEntity subscription, GameWebhookEventType eventType) {
        return Arrays.stream(subscription.getEventTypesCsv().split(","))
                .map(String::trim)
                .anyMatch(value -> value.equals(eventType.name()));
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
