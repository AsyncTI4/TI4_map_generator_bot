package ti4.discord.interactions.routing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Button ids are routed to the LONGEST registered {@code @ButtonHandler} key that is a prefix of the id. So registering
 * a key like {@code exhaustAgent_kaloraagent} silently steals every press starting with it from {@code exhaustAgent_},
 * with no warning at startup.
 *
 * <p>This test lists every (shorter key, longer key) pair where the shorter key is a prefix of the longer one and
 * compares it with a checked-in allowlist of the pairs that are known and intended. A new pair fails the build so the
 * developer has to confirm the shadowing is deliberate; a pair that no longer exists fails too, so the list stays exact.
 */
class ButtonHandlerPrefixShadowingTest {

    private static final String SEPARATOR = " -> ";
    private static final String ALLOWLIST_RESOURCE = "/routing/button-handler-prefix-shadowing-allowlist.txt";
    private static final String ALLOWLIST_SOURCE =
            "src/test/resources/routing/button-handler-prefix-shadowing-allowlist.txt";
    private static final Path CURRENT_PAIRS_OUTPUT = Path.of("target", "button-handler-prefix-shadowing-current.txt");

    @Test
    void everyShadowingPairIsAllowlisted() throws IOException {
        Set<String> current = shadowingPairs(RegisteredHandlerKeys.buttonKeys());
        writeCurrentPairs(current);
        Set<String> allowed = readAllowlist();

        Set<String> unexpected = new TreeSet<>(current);
        unexpected.removeAll(allowed);
        Set<String> stale = new TreeSet<>(allowed);
        stale.removeAll(current);

        assertTrue(
                unexpected.isEmpty(),
                () -> "New @ButtonHandler prefix shadowing detected. Button ids go to the LONGEST registered key that"
                        + " prefixes them, so for each pair below every press whose id starts with the longer key no"
                        + " longer reaches the shorter key's handler.\n"
                        + "If that is intended, add the line(s) to " + ALLOWLIST_SOURCE + ". Otherwise rename the"
                        + " longer key so it does not start with the shorter one.\n"
                        + "  " + String.join("\n  ", unexpected)
                        + "\n(The full current list is in " + CURRENT_PAIRS_OUTPUT + ".)");
        assertTrue(
                stale.isEmpty(),
                () -> "These allowlisted pairs no longer exist; remove them from " + ALLOWLIST_SOURCE + ":\n  "
                        + String.join("\n  ", stale));
    }

    @Test
    void keysCanBeWrittenToTheAllowlistFormat() {
        List<String> clashing = RegisteredHandlerKeys.buttonKeys().stream()
                .filter(key -> key.contains(SEPARATOR.strip()) || key.startsWith("#") || !key.equals(key.strip()))
                .toList();

        assertTrue(clashing.isEmpty(), () -> "Keys that the allowlist format cannot represent: " + clashing);
    }

    static Set<String> shadowingPairs(Set<String> keys) {
        Set<String> pairs = new TreeSet<>();
        for (String longer : keys) {
            for (int length = 0; length < longer.length(); length++) {
                String shorter = longer.substring(0, length);
                if (keys.contains(shorter)) pairs.add(shorter + SEPARATOR + longer);
            }
        }
        return pairs;
    }

    private static Set<String> readAllowlist() throws IOException {
        Set<String> allowed = new LinkedHashSet<>();
        try (InputStream stream = ButtonHandlerPrefixShadowingTest.class.getResourceAsStream(ALLOWLIST_RESOURCE)) {
            assertNotNull(stream, "Missing allowlist " + ALLOWLIST_SOURCE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#') continue;
                allowed.add(trimmed);
            }
        }
        return allowed;
    }

    private static void writeCurrentPairs(Set<String> current) throws IOException {
        Files.createDirectories(CURRENT_PAIRS_OUTPUT.getParent());
        Files.write(CURRENT_PAIRS_OUTPUT, new ArrayList<>(current), StandardCharsets.UTF_8);
    }
}
