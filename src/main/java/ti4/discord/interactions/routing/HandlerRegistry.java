package ti4.discord.interactions.routing;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import ti4.discord.interactions.listeners.context.ListenerContext;
import ti4.logging.RollbarManager;

public record HandlerRegistry<C extends ListenerContext>(Map<String, Handler<C>> handlers) {

    record Handler<C extends ListenerContext>(Consumer<C> consumer, boolean shouldSave) {}

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

    private Handler<C> findHandler(String componentId) {
        if (componentId == null) return null;
        String key = findMatchedKey(componentId);
        return key == null ? null : handlers.get(key);
    }

    private String findMatchedKey(String componentId) {
        if (handlers.containsKey(componentId)) return componentId;

      return handlers.keySet()
          .stream()
          .filter(componentId::startsWith)
          .max(Comparator.comparingInt(String::length))
          .orElse(null);
    }
}
