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
 * replays and offers Temporal Command Suite. Exhausting a leader any other way skips all of that, so only the files
 * below may still do it, each for a stated reason.
 */
class DirectAgentExhaustGuardTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    private static final Set<String> MAY_CALL_EXHAUST_LEADER_SERVICE = Set.of(
            // The lifecycle itself.
            "AgentLifecycle.java",
            // The manual /leader exhaust command, for any leader type.
            "ExhaustLeader.java",
            // Envoys are not agents.
            "CryypterHelper.java",
            // These exhaust another player's agent; nobody is using it.
            "SimulacrumAcd2ButtonHandler.java",
            "ZephyrionBreakthroughHandler.java");

    private static final Set<String> MAY_SET_A_LEADER_EXHAUSTED = Set.of(
            // The service every exhaust goes through.
            "ExhaustLeaderService.java",
            // Heroes, not agents.
            "ButtonHelperHeroes.java",
            // Ssruu used as the JR-XS455-O relic, which is not an agent use.
            "ButtonHelperFactionSpecific.java",
            // Exhausting an agent is the breakthrough's cost, not a use of the agent.
            "ThundersParadoxService.java",
            // Developer testbed setup.
            "TestBedApplyService.java");

    private static final Set<String> STILL_SPEND_WITHOUT_A_MODULE = Set.of(
            // Homebrew handlers that have not been moved into agent modules yet. Shrink this list, never grow it.
            "ArcanumLeadersHandler.java",
            "KairnLeadershandler.java",
            "MyrrLeadersHandler.java",
            "OblivionLeadersHandler.java",
            "RevenantLeadersHandler.java",
            "VerydithLeadersHandler.java",
            "XytherisLeadersHandler.java");

    @Test
    void onlyTheUnmigratedHomebrewHandlersStillCallSpend() throws IOException {
        assertThat(filesContaining("AgentLifecycle.spend("))
                .as("an agent with a module resolves through AgentLifecycle.use or press, never spend")
                .containsExactlyInAnyOrderElementsOf(STILL_SPEND_WITHOUT_A_MODULE);
    }

    @Test
    void onlyTheKnownFilesCallExhaustLeaderService() throws IOException {
        assertThat(filesContaining("ExhaustLeaderService.exhaustLeader("))
                .as("spend an agent with AgentLifecycle.spend so the use is recorded and TCS is offered")
                .containsExactlyInAnyOrderElementsOf(MAY_CALL_EXHAUST_LEADER_SERVICE);
    }

    @Test
    void onlyTheKnownFilesSetALeaderExhausted() throws IOException {
        assertThat(filesContaining(".setExhausted(true)"))
                .as("spend an agent with AgentLifecycle.spend so the use is recorded and TCS is offered")
                .containsExactlyInAnyOrderElementsOf(MAY_SET_A_LEADER_EXHAUSTED);
    }

    private static List<String> filesContaining(String code) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> contains(file, code))
                    .map(file -> file.getFileName().toString())
                    .toList();
        }
    }

    private static boolean contains(Path file, String code) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(code);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
