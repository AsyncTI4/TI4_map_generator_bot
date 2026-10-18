package ti4.discord.interactions.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.listeners.context.ButtonContext;

/**
 * {@link HandlerRegistry#findMatchedKey} used to be an exact lookup followed by a linear scan over every key, keeping
 * the longest key that prefixes the id. It now probes the id's own prefixes from longest to shortest. These tests pin
 * that both algorithms pick the same key.
 */
class HandlerRegistryMatchingTest {

    @Test
    void exactMatchWins() {
        HandlerRegistry<ButtonContext> registry = registryOf(Set.of("abc", "abc_", "ab"));

        assertThat(registry.findMatchedKey("abc")).isEqualTo("abc");
        assertThat(registry.findMatchedKey("abc_")).isEqualTo("abc_");
    }

    @Test
    void longestRegisteredPrefixWins() {
        HandlerRegistry<ButtonContext> registry = registryOf(Set.of("exhaustAgent_", "exhaustAgent_kalora", "e"));

        assertThat(registry.findMatchedKey("exhaustAgent_kaloraagent")).isEqualTo("exhaustAgent_kalora");
        assertThat(registry.findMatchedKey("exhaustAgent_hacan")).isEqualTo("exhaustAgent_");
        assertThat(registry.findMatchedKey("exhaust")).isEqualTo("e");
    }

    @Test
    void noMatch() {
        HandlerRegistry<ButtonContext> registry = registryOf(Set.of("abc_"));

        assertThat(registry.findMatchedKey("abc")).isNull();
        assertThat(registry.findMatchedKey("xyz_abc_")).isNull();
        assertThat(registry.findMatchedKey("")).isNull();
        assertThat(registry.findMatchedKey(null)).isNull();
    }

    @Test
    void emptyKeyStillMatchesEverythingLikeTheOldScan() {
        HandlerRegistry<ButtonContext> registry = registryOf(Set.of("", "abc_"));

        assertThat(registry.findMatchedKey("anything")).isEqualTo("");
        assertThat(registry.findMatchedKey("")).isEqualTo("");
        assertThat(registry.findMatchedKey("abc_1")).isEqualTo("abc_");
    }

    @Test
    void idsLongerThanEveryKeyStillMatch() {
        HandlerRegistry<ButtonContext> registry = registryOf(Set.of("a_"));

        assertThat(registry.findMatchedKey("a_" + "x".repeat(200))).isEqualTo("a_");
    }

    @Test
    void unmatchedRouteTakesTheWriteLockAndDoesNotDispatch() {
        HandlerRegistry<ButtonContext> registry = registryOf(Set.of("abc_"));

        HandlerRegistry.Route<ButtonContext> route = registry.resolve("nothingHere");

        assertThat(route.isMatched()).isFalse();
        assertThat(route.shouldSave()).isTrue();
        assertThat(route.dispatch(null)).isFalse();
    }

    @Test
    void buttonKeysMatchLikeTheLinearScan() {
        assertMatchesLikeLinearScan(RegisteredHandlerKeys.buttonKeys());
    }

    @Test
    void modalKeysMatchLikeTheLinearScan() {
        assertMatchesLikeLinearScan(RegisteredHandlerKeys.modalKeys());
    }

    @Test
    void selectionKeysMatchLikeTheLinearScan() {
        assertMatchesLikeLinearScan(RegisteredHandlerKeys.selectionKeys());
    }

    private static void assertMatchesLikeLinearScan(Set<String> keys) {
        assertThat(keys).isNotEmpty();
        HandlerRegistry<ButtonContext> registry = registryOf(keys);

        List<String> mismatches = new ArrayList<>();
        for (String id : realisticIds(keys)) {
            String expected = linearScan(keys, id);
            String actual = registry.findMatchedKey(id);
            if (!Objects.equals(expected, actual)) {
                mismatches.add("id `" + id + "`: linear scan -> `" + expected + "`, registry -> `" + actual + "`");
            }
        }

        assertThat(mismatches).isEmpty();
    }

    // Ids built from the real keys: the key itself, the key with typical payloads appended, truncations that
    // should fall back to a shorter key (or none), and two keys glued together to reach deeper prefix chains.
    private static Set<String> realisticIds(Set<String> keys) {
        List<String> sorted = new ArrayList<>(keys);
        Set<String> ids = new LinkedHashSet<>(List.of("", "x", "unregisteredButton_123"));
        for (int i = 0; i < sorted.size(); i++) {
            String key = sorted.get(i);
            String next = sorted.get((i + 1) % sorted.size());
            ids.add(key);
            ids.add(key + "1");
            ids.add(key + "_hacan");
            ids.add(key + "hacan_18_space");
            ids.add(key + "deleteThis");
            ids.add(key + next);
            if (!key.isEmpty()) {
                ids.add(key.substring(0, key.length() - 1));
                ids.add(key.substring(0, key.length() / 2));
            }
        }
        return ids;
    }

    private static String linearScan(Set<String> keys, String id) {
        if (keys.contains(id)) return id;
        return keys.stream()
                .filter(id::startsWith)
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }

    private static HandlerRegistry<ButtonContext> registryOf(Set<String> keys) {
        HandlerRegistry<ButtonContext> registry = new HandlerRegistry<>();
        for (String key : keys) registry.register(key, context -> {}, true);
        return registry;
    }
}
