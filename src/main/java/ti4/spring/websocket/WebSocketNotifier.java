package ti4.spring.websocket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ti4.game.Game;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.website.model.WebGameState;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Single contract for pushing game updates to web clients.
 *
 * <p>{@code notifyGameRefresh} signals that the heavy web-data payload changed
 * (map render pipeline). {@code notifyGameStateChanged} owns the lightweight
 * realtime channel: it rebuilds the small {@link WebGameState} snapshot,
 * decides whether anything actually changed against an in-memory baseline,
 * and publishes only the changed fields as an RFC 7386-style merge patch
 * (client deep-merges; {@code null} deletes a key; arrays replace wholesale).
 * Nothing is persisted, so undo needs no special handling: the post-undo
 * snapshot diffs against the last published one like any other change.</p>
 */
@RequiredArgsConstructor
@Component
public class WebSocketNotifier {
    private final SimpMessagingTemplate messagingTemplate;

    private final Cache<String, JsonNode> lastPublishedState = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofHours(6))
            .build();
    private final Map<String, AtomicLong> stateSequences = new ConcurrentHashMap<>();

    public void notifyGameRefresh(String gameId) {
        String destination = "/topic/game/" + gameId;
        messagingTemplate.convertAndSend(destination, "refresh");
    }

    /** Static entry point for non-Spring callers (GameManager, GameUndoService). Never throws. */
    public static void notifyGameStateChange(Game game) {
        try {
            SpringContext.getBean(WebSocketNotifier.class).notifyGameStateChanged(game);
        } catch (Exception ignored) {
            // Spring context may not be up during warmup; state streams on the next save.
        }
    }

    public void notifyGameStateChanged(Game game) {
        try {
            if (game == null || game.isFowMode() || System.getenv("TESTING") != null) return;

            String gameId = game.getName();
            JsonNode current = JsonMapperManager.basic().valueToTree(WebGameState.fromGame(game));
            JsonNode previous = lastPublishedState.getIfPresent(gameId);

            boolean full = previous == null;
            JsonNode patch = full ? current : diff(previous, current);
            if (patch == null) return; // nothing changed

            lastPublishedState.put(gameId, current);
            long seq = stateSequences
                    .computeIfAbsent(gameId, k -> new AtomicLong())
                    .incrementAndGet();

            String json = JsonMapperManager.basic()
                    .writeValueAsString(
                            new GameStateMessage("gameState", seq, System.currentTimeMillis(), full, patch));
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", json);
        } catch (Exception e) {
            // Never let websocket publishing break a game save.
            BotLogger.error("Failed to publish game state update", e);
        }
    }

    /**
     * Merge patch of {@code after} relative to {@code before}: only changed keys,
     * {@code null} for removed/nulled keys, arrays and scalars replaced wholesale.
     * Returns null when the trees are identical.
     */
    private static JsonNode diff(JsonNode before, JsonNode after) {
        if (before.equals(after)) return null;
        if (!before.isObject() || !after.isObject()) return after;

        ObjectNode patch = JsonMapperManager.basic().createObjectNode();
        Set<String> fieldNames = new HashSet<>();
        before.propertyNames().forEach(fieldNames::add);
        after.propertyNames().forEach(fieldNames::add);
        for (String field : fieldNames) {
            JsonNode beforeValue = before.path(field);
            JsonNode afterValue = after.path(field);
            if (afterValue.isMissingNode()) {
                patch.putNull(field); // key disappeared -> delete on client
            } else {
                JsonNode childPatch = diff(beforeValue, afterValue);
                if (childPatch != null) patch.set(field, childPatch);
            }
        }
        return patch.isEmpty() ? null : patch;
    }

    public record GameStateMessage(String type, long seq, long timestamp, boolean full, JsonNode patch) {}
}
