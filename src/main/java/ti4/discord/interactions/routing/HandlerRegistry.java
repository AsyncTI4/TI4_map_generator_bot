package ti4.discord.interactions.routing;

import java.util.Map;
import java.util.function.Consumer;
import ti4.discord.interactions.listeners.context.ListenerContext;

/**
 * Bundles the handler consumer map with the corresponding save-flag map for a single interaction type.
 *
 * @param <C> The {@link ListenerContext} subtype handled by this registry.
 * @param handlers Map of component-ID prefix → handler consumer.
 * @param saveFlags Map of component-ID prefix → whether the handler persists game state.
 */
public record HandlerRegistry<C extends ListenerContext>(
        Map<String, Consumer<C>> handlers, Map<String, Boolean> saveFlags) {

    /**
     * Determines whether handling the given {@code componentID} should save game state.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Exact match in {@code saveFlags}</li>
     *   <li>Longest prefix match in {@code saveFlags}</li>
     *   <li>Defaults to {@code true} (WRITE lock) when no match is found</li>
     * </ol>
     *
     * @param componentID The raw component ID from the Discord interaction event.
     * @return {@code true} if the matched handler saves game state (or no match — defaults to WRITE).
     */
    public boolean isSave(String componentID) {
        if (componentID == null) return true;

        Boolean exact = saveFlags.get(componentID);
        if (exact != null) return exact;

        String longestMatch = null;
        for (String key : saveFlags.keySet()) {
            if (componentID.startsWith(key)) {
                if (longestMatch == null || key.length() > longestMatch.length()) {
                    longestMatch = key;
                }
            }
        }
        return longestMatch == null || saveFlags.get(longestMatch);
    }
}
