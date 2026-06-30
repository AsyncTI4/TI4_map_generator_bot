package ti4.spring.service.webhook;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import ti4.service.webhook.GameWebhookEventType;

@Service
public class GameWebhookSubscriptionService {
    private final GameWebhookRepository gameWebhookRepository;
    private final WebhookUserRepository webhookUserRepository;
    private final Argon2PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 1 << 16, 3);

    public GameWebhookSubscriptionService(
            GameWebhookRepository gameWebhookRepository, WebhookUserRepository webhookUserRepository) {
        this.gameWebhookRepository = gameWebhookRepository;
        this.webhookUserRepository = webhookUserRepository;
    }

    public List<PublicGameWebhookEventType> getEventTypes() {
        return Arrays.stream(PublicGameWebhookEventType.values()).toList();
    }

    public WebhookUserEntity authenticateWebhookUser(String apiKey) {
        if (StringUtils.isBlank(apiKey)) {
            return null;
        }
        return webhookUserRepository.findByActiveTrue().stream()
                .filter(user -> StringUtils.isNotBlank(user.getApiKeyHash()))
                .filter(user -> passwordEncoder.matches(apiKey, user.getApiKeyHash()))
                .findFirst()
                .orElse(null);
    }

    public void upsertSubscription(String gameName, long webhookUserId, Set<PublicGameWebhookEventType> eventTypes) {
        GameWebhookEntity entity = new GameWebhookEntity();
        entity.setGameName(gameName);
        entity.setWebhookUserId(webhookUserId);
        entity.setEventTypesCsv(serializeEventTypes(eventTypes));
        gameWebhookRepository.save(entity);
    }

    public void deleteSubscription(String gameName, long webhookUserId) {
        gameWebhookRepository.deleteByGameNameAndWebhookUserId(gameName, webhookUserId);
    }

    public List<String> getCallbackUrls(String gameName, GameWebhookEventType eventType) {
        PublicGameWebhookEventType publicEventType =
                PublicGameWebhookEventType.fromInternal(eventType).orElse(null);
        if (publicEventType == null) {
            return List.of();
        }

        List<GameWebhookEntity> subscriptions = gameWebhookRepository.findByGameName(gameName);
        if (subscriptions.isEmpty()) {
            return List.of();
        }

        Set<Long> webhookUserIds =
                subscriptions.stream().map(GameWebhookEntity::getWebhookUserId).collect(Collectors.toSet());
        Map<Long, WebhookUserEntity> usersById = webhookUserRepository.findAllById(webhookUserIds).stream()
                .filter(WebhookUserEntity::isActive)
                .collect(Collectors.toMap(WebhookUserEntity::getId, user -> user));

        Set<String> callbackUrls = new LinkedHashSet<>();
        for (GameWebhookEntity subscription : subscriptions) {
            WebhookUserEntity user = usersById.get(subscription.getWebhookUserId());
            if (user == null || StringUtils.isBlank(user.getCallbackUrl())) {
                continue;
            }
            EnumSet<PublicGameWebhookEventType> subscribedTypes = parseEventTypes(subscription.getEventTypesCsv());
            if (subscribedTypes.contains(publicEventType)) {
                callbackUrls.add(user.getCallbackUrl());
            }
        }
        return List.copyOf(callbackUrls);
    }

    private static EnumSet<PublicGameWebhookEventType> parseEventTypes(String csv) {
        EnumSet<PublicGameWebhookEventType> eventTypes = EnumSet.noneOf(PublicGameWebhookEventType.class);
        if (StringUtils.isBlank(csv)) {
            return eventTypes;
        }
        for (String entry : csv.split(",")) {
            PublicGameWebhookEventType.parse(entry).ifPresent(eventTypes::add);
        }
        return eventTypes;
    }

    private static String serializeEventTypes(Set<PublicGameWebhookEventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return "";
        }
        return eventTypes.stream()
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));
    }
}
