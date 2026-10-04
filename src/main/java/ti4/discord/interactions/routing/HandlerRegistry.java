package ti4.discord.interactions.routing;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import ti4.discord.interactions.listeners.context.ListenerContext;
import ti4.logging.RollbarManager;

public class HandlerRegistry<C extends ListenerContext> {

    private final Map<String, Route<C>> routes = new HashMap<>();
    private int longestKeyLength;

    public void register(String key, Consumer<C> consumer, boolean shouldSave) {
        routes.put(key, new Route<>(key, consumer, shouldSave));
        longestKeyLength = Math.max(longestKeyLength, key.length());
    }

    public Route<C> resolve(String rawComponentId) {
        String key = findMatchedKey(ComponentIdEnvelope.decode(rawComponentId).handlerId());
        return key == null ? Route.unmatched() : routes.get(key);
    }

    String findMatchedKey(String componentId) {
        if (componentId == null) return null;
        for (int length = Math.min(componentId.length(), longestKeyLength); length >= 0; length--) {
            String candidate = componentId.substring(0, length);
            if (routes.containsKey(candidate)) return candidate;
        }
        return null;
    }

    public int getSize() {
        return routes.size();
    }

    public record Route<C extends ListenerContext>(String key, Consumer<C> consumer, boolean shouldSave) {

        private static <C extends ListenerContext> Route<C> unmatched() {
            return new Route<>(null, null, true);
        }

        public boolean isMatched() {
            return key != null;
        }

        public boolean dispatch(C context) {
            if (!isMatched()) return false;
            RollbarManager.put("handler_id", key);
            consumer.accept(context);
            return true;
        }
    }
}
