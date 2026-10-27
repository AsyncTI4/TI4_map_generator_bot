package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

/**
 * Pins which agents have no module, so a press only exhausts and announces the card.
 *
 * <p>An agent whose ability window is "ACTION:" is listed in the component-action menu, which calls
 * AgentLifecycle.press with the bare leader id. Without a module that press does nothing more, so a
 * new action agent must either get a module or be added here on purpose. Remove ids from this list as they get
 * modules.
 */
class LegacyAgentFallThroughGuardTest extends BaseTi4Test {

    private static final Set<String> ACTION_AGENTS_WITHOUT_A_MODULE =
            Set.of("arachneraagent", "bsp_xxchaagent", "horatioagent", "lumerisagent", "psmagent", "uchoiragent");

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Pattern AGENT_SELECTION = Pattern.compile("getAgentSelection_([a-z0-9]+)");

    @Test
    void onlyTheKnownActionAgentsLackAModule() {
        List<String> withoutModule = Mapper.getLeaders().values().stream()
                .filter(leader -> "agent".equals(leader.getType()))
                .filter(leader -> "ACTION:".equalsIgnoreCase(leader.getAbilityWindow()))
                .map(LeaderModel::getId)
                .filter(id -> AgentModules.find(id).isEmpty())
                .toList();

        assertThat(withoutModule).containsExactlyInAnyOrderElementsOf(ACTION_AGENTS_WITHOUT_A_MODULE);
    }

    @Test
    void everyUseOnSomeoneElseButtonReachesAModule() throws IOException {
        List<String> selectedAgents;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            selectedAgents = files.filter(file -> file.toString().endsWith(".java"))
                    .flatMap(LegacyAgentFallThroughGuardTest::agentSelections)
                    .distinct()
                    .toList();
        }

        assertThat(selectedAgents).isNotEmpty();
        assertThat(selectedAgents)
                .as("a getAgentSelection_<id> picker sends exhaustAgent_<id>_<faction>, which needs a module")
                .allSatisfy(id -> assertThat(AgentModules.find(id)).isPresent());
    }

    private static Stream<String> agentSelections(Path file) {
        try {
            Matcher matcher = AGENT_SELECTION.matcher(Files.readString(file, StandardCharsets.UTF_8));
            return matcher.results().map(result -> result.group(1));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
