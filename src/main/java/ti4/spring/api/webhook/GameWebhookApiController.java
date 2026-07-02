package ti4.spring.api.webhook;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.service.webhook.GameWebhookEventType;
import ti4.spring.context.SetupRequestContext;
import ti4.spring.service.webhook.GameWebhookSubscriptionService;
import ti4.spring.service.webhook.GameWebhookSubscriptionService.SubscribeResult;

@RestController
@RequestMapping("/api/public/game")
public class GameWebhookApiController {

    private final GameWebhookSubscriptionService subscriptionService;

    public GameWebhookApiController(GameWebhookSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/webhook/eventTypes")
    public List<GameWebhookEventTypeResponse> getEventTypes() {
        return Arrays.stream(GameWebhookEventType.values())
                .map(GameWebhookEventTypeResponse::from)
                .toList();
    }

    @SetupRequestContext(false)
    @PutMapping("/{gameId}/webhook")
    public ResponseEntity<Void> put(
            @PathVariable("gameId") String gameName,
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @RequestBody GameWebhookSubscriptionRequest request) {
        if (apiKey == null || apiKey.isBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return response(subscriptionService.subscribe(gameName, apiKey, request == null ? null : request.eventTypes()));
    }

    @SetupRequestContext(false)
    @DeleteMapping("/{gameId}/webhook")
    public ResponseEntity<Void> delete(
            @PathVariable("gameId") String gameName, @RequestHeader(name = "X-API-Key", required = false) String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return response(subscriptionService.delete(gameName, apiKey));
    }

    private static ResponseEntity<Void> response(SubscribeResult result) {
        return switch (result) {
            case OK -> ResponseEntity.ok().build();
            case UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case UNKNOWN_GAME -> ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            case FORBIDDEN_GAME -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            case UNSUPPORTED_EVENT_TYPE -> ResponseEntity.badRequest().build();
        };
    }
}
