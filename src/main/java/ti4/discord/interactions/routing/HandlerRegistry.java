package ti4.discord.interactions.routing;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import ti4.discord.interactions.listeners.context.ListenerContext;
import ti4.logging.RollbarManager;

public class HandlerRegistry<C extends ListenerContext> {

    private final Map<String, Handler<C>> handlers = new HashMap<>();

    public void register(String key, Consumer<C> consumer, boolean shouldSave) {
        handlers.put(key, new Handler<>(consumer, shouldSave));
    }

    public boolean isSave(String componentId) {
        Handler<C> handler = findHandler(componentId);
        return handler == null || handler.shouldSave();
    }

    public boolean handle(String componentId, C context) {
        if (componentId == null) return false;

        String matchedKey = findMatchedKey(componentId);
        if (matchedKey == null) return false;

        RollbarManager.put("handler_id", matchedKey);
        handlers.get(matchedKey).consumer().accept(context);
        return true;
    }

    public String findHandlerKey(String componentId) {
        return findMatchedKey(componentId);
    }

    private Handler<C> findHandler(String componentId) {
        if (componentId == null) return null;
        String key = findMatchedKey(componentId);
        return key == null ? null : handlers.get(key);
    }

    private String findMatchedKey(String componentId) {
        if (handlers.containsKey(componentId)) return componentId;

        return handlers.keySet().stream()
                .filter(componentId::startsWith)
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }

    public int getSize() {
        return handlers.size();
    }

    private record Handler<C extends ListenerContext>(Consumer<C> consumer, boolean shouldSave) {}
}
