package ti4.spring.api.webhook;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.context.SetupRequestContext;
import ti4.spring.service.webhook.GameWebhookSubscriptionService;
import ti4.spring.service.webhook.PublicGameWebhookEventType;
import ti4.spring.service.webhook.WebhookUserEntity;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/game")
public class GameWebhookApiController {

    private final GameWebhookSubscriptionService gameWebhookSubscriptionService;

    @GetMapping("/webhook/eventTypes")
    public List<GameWebhookEventTypeResponse> eventTypes() {
        return gameWebhookSubscriptionService.getEventTypes().stream()
                .map(type -> new GameWebhookEventTypeResponse(type.name(), type.description()))
                .toList();
    }

    @PutMapping("/{gameName}/webhook")
    @SetupRequestContext(save = false)
    public ResponseEntity<Void> upsert(
            @PathVariable String gameName,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) GameWebhookSubscriptionRequest request) {
        WebhookUserEntity webhookUser = gameWebhookSubscriptionService.authenticateWebhookUser(apiKey);
        if (webhookUser == null) {
            return ResponseEntity.status(401).build();
        }

        Set<PublicGameWebhookEventType> eventTypes = sanitizeEventTypes(request == null ? null : request.eventTypes());
        if (eventTypes.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        gameWebhookSubscriptionService.upsertSubscription(gameName, webhookUser.getId(), eventTypes);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{gameName}/webhook")
    @SetupRequestContext(save = false)
    public ResponseEntity<Void> delete(
            @PathVariable String gameName,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        WebhookUserEntity webhookUser = gameWebhookSubscriptionService.authenticateWebhookUser(apiKey);
        if (webhookUser == null) {
            return ResponseEntity.status(401).build();
        }

        gameWebhookSubscriptionService.deleteSubscription(gameName, webhookUser.getId());
        return ResponseEntity.ok().build();
    }

    private static Set<PublicGameWebhookEventType> sanitizeEventTypes(List<PublicGameWebhookEventType> eventTypes) {
        if (eventTypes == null) {
            return Set.of();
        }
        return eventTypes.stream().filter(type -> type != null).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
