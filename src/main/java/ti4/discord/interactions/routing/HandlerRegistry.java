package ti4.discord.interactions.routing;

import java.util.Map;
import java.util.function.Consumer;
import ti4.discord.interactions.listeners.context.ListenerContext;
import ti4.logging.RollbarManager;

/**
 * Maps component-ID prefixes to {@link Handler} instances for a single interaction type.
 *
 * @param <C> The {@link ListenerContext} subtype handled by this registry.
 * @param handlers Map of component-ID prefix → {@link Handler}.
 */
public record HandlerRegistry<C extends ListenerContext>(Map<String, Handler<C>> handlers) {

    /**
     * Holds a handler consumer together with the flag that says whether it persists game state.
     *
     * @param <C> The {@link ListenerContext} subtype.
     * @param consumer The action to run when this handler is invoked.
     * @param save     Whether the game should be saved after the handler runs.
     */
    public record Handler<C extends ListenerContext>(Consumer<C> consumer, boolean save) {
        /** Returns the consumer for this handler. */
        public Consumer<C> consumer() {
            return consumer;
        }

        /** Returns whether this handler saves game state. */
        public boolean isSave() {
            return save;
        }
    }

    /**
     * Determines whether handling the given {@code componentID} should save game state.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Exact match in {@code handlers}</li>
     *   <li>Longest prefix match in {@code handlers}</li>
     *   <li>Defaults to {@code true} (WRITE lock) when no match is found</li>
     * </ol>
     *
     * @param componentID The raw component ID from the Discord interaction event.
     * @return {@code true} if the matched handler saves game state (or no match — defaults to WRITE).
     */
    public boolean isSave(String componentID) {
        Handler<C> handler = findHandler(componentID);
        return handler == null || handler.isSave();
    }

    /**
     * Dispatches {@code context} to the best-matching handler for {@code componentID}.
     *
     * <p>Uses exact match first, then longest-prefix match.
     *
     * @param componentID The raw component ID from the Discord interaction event.
     * @param context     The interaction context to pass to the handler.
     * @return {@code true} if a handler was found and invoked, {@code false} otherwise.
     */
    public boolean handle(String componentID, C context) {
        if (componentID == null) return false;

        // Check for exact match first
        Handler<C> exact = handlers.get(componentID);
        if (exact != null) {
            RollbarManager.put("handler_id", componentID);
            exact.consumer().accept(context);
            return true;
        }

        // Then check for longest prefix match
        String longestMatch = null;
        for (String key : handlers.keySet()) {
            if (componentID.startsWith(key)) {
                if (longestMatch == null || key.length() > longestMatch.length()) {
                    longestMatch = key;
                }
            }
        }
        if (longestMatch != null) {
            RollbarManager.put("handler_id", longestMatch);
            handlers.get(longestMatch).consumer().accept(context);
            return true;
        }
        return false;
    }

    private Handler<C> findHandler(String componentID) {
        if (componentID == null) return null;

        Handler<C> exact = handlers.get(componentID);
        if (exact != null) return exact;

        String longestMatch = null;
        for (String key : handlers.keySet()) {
            if (componentID.startsWith(key)) {
                if (longestMatch == null || key.length() > longestMatch.length()) {
                    longestMatch = key;
                }
            }
        }
        return longestMatch == null ? null : handlers.get(longestMatch);
    }
}
