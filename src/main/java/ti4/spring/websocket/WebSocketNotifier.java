package ti4.spring.websocket;

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
import ti4.spring.api.webdata.GameWebDataService;
import ti4.spring.context.SpringContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Single contract for pushing game updates to web clients.
 *
 * <p>{@code notifyGameRefresh} signals that the heavy web-data payload changed
 * (map render pipeline). {@code notifyGameStateChanged} owns the realtime
 * channel: it rebuilds the full web-exposed payload
 * ({@link GameWebDataService#buildWebData} — gameState, objectives, playerData,
 * tiles, card pool, laws, strategy cards, ...), decides whether anything
 * actually changed against an in-memory baseline, and publishes only the
 * changed fields as an RFC 7386-style merge patch (client deep-merges;
 * {@code null} deletes a key; arrays replace wholesale). The baseline is the
 * serialized payload in {@link GameWebDataService}'s cache — the same cache
 * that serves the REST endpoint — so the two views can never drift. Nothing is
 * persisted, so undo needs no special handling: the post-undo snapshot diffs
 * against the last published one like any other change; a cache eviction just
 * costs one full snapshot resend.</p>
 */
@RequiredArgsConstructor
@Component
public class WebSocketNotifier {
    private static final JsonMapper MAPPER = JsonMapperManager.basic();

    private final SimpMessagingTemplate messagingTemplate;
    private final GameWebDataService gameWebDataService;

    private final Map<String, AtomicLong> stateSequences = new ConcurrentHashMap<>();
    private final Map<String, Object> perGameLocks = new ConcurrentHashMap<>();

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
            if (game == null || System.getenv("TESTING") != null) return;

            String gameId = game.getName();
            // Build the snapshot outside the lock (pure computation, no shared state).
            JsonNode current = MAPPER.valueToTree(GameWebDataService.buildWebData(game));

            synchronized (perGameLocks.computeIfAbsent(gameId, k -> new Object())) {
                // Sole writer of the web-data cache: if anything else wrote it, the diff
                // baseline would drift from what clients last received.
                if (game.isFowMode()) {
                    gameWebDataService.put(gameId, MAPPER.writeValueAsString(current));
                    return; // cache refreshed for REST, but FoW games never stream
                }
                String previousJson = gameWebDataService.getIfCached(gameId);
                JsonNode previous = previousJson == null ? null : MAPPER.readTree(previousJson);

                boolean full = previous == null;
                JsonNode patch = full ? current : diff(previous, current);
                if (patch == null) return; // nothing changed

                gameWebDataService.put(gameId, MAPPER.writeValueAsString(current));
                long seq = stateSequences
                        .computeIfAbsent(gameId, k -> new AtomicLong())
                        .incrementAndGet();

                String json = MAPPER.writeValueAsString(
                        new GameStateMessage("gameState", seq, System.currentTimeMillis(), full, patch));
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", json);
            }
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

        ObjectNode patch = MAPPER.createObjectNode();
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
