package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Agents are spent through AgentLifecycle (press, use or spend), which records the use, mirrors it to combat
 * replays and offers Temporal Command Suite. ExhaustLeaderService skips all of that, so only the callers below may
 * still exhaust a leader directly.
 */
class DirectAgentExhaustGuardTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final String DIRECT_EXHAUST = "ExhaustLeaderService.exhaustLeader(";

    private static final Set<String> ALLOWED = Set.of(
            // The lifecycle itself.
            "AgentLifecycle.java",
            // The manual /leader exhaust command, for any leader type.
            "ExhaustLeader.java",
            // Envoys are not agents.
            "CryypterHelper.java",
            // These exhaust another player's agent; nobody is using it.
            "SimulacrumAcd2ButtonHandler.java",
            "ZephyrionBreakthroughHandler.java");

    @Test
    void onlyTheKnownCallersExhaustALeaderDirectly() throws IOException {
        List<String> callers;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            callers = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(DirectAgentExhaustGuardTest::exhaustsDirectly)
                    .map(file -> file.getFileName().toString())
                    .toList();
        }

        assertThat(callers)
                .as("spend an agent with AgentLifecycle.spend so the use is recorded and TCS is offered")
                .containsExactlyInAnyOrderElementsOf(ALLOWED);
    }

    private static boolean exhaustsDirectly(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(DIRECT_EXHAUST);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
