package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.discord.interactions.routing.AnnotationHandlerTestAccess;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.service.leader.LeaderAgents;
import ti4.testUtils.BaseTi4Test;

/**
 * Guards agent modules against a longer-prefix button handler stealing the press, and against ids that the
 * exhaustAgent_ button format cannot carry.
 *
 * <p>Button routing picks the longest matching prefix, so a {@code @ButtonHandler("exhaustAgent_<id>")} would
 * silently bypass the module. And the id in an {@code exhaustAgent_<id>_<payload>} button ends at the first
 * underscore, so a module whose id contains one could never be reached that way.
 */
class AgentModuleRoutingGuardTest extends BaseTi4Test {

    private static final Set<String> IDS_WITHOUT_AN_EXHAUST_AGENT_BUTTON = Set.of("edynagent_br", "yssarilagent_br");

    @Test
    void noButtonHandlerShadowsAMigratedAgent() {
        List<String> registeredIds = registeredAgentIds();

        List<String> shadowing = new ArrayList<>();
        for (Class<?> klass : AnnotationHandlerTestAccess.allHandlerClasses()) {
            for (Method method : klass.getDeclaredMethods()) {
                for (ButtonHandler handler : method.getAnnotationsByType(ButtonHandler.class)) {
                    String key = handler.value().toLowerCase(Locale.ROOT);
                    if (key.length() <= AgentButtonIds.PREFIX.length()
                            || !key.startsWith(AgentButtonIds.PREFIX.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    String agentId =
                            AgentButtonIds.parse(handler.value()).agentId().toLowerCase(Locale.ROOT);
                    if (registeredIds.contains(agentId)) {
                        shadowing.add(klass.getSimpleName() + "#" + method.getName() + " -> " + handler.value());
                    }
                }
            }
        }

        assertThat(shadowing)
                .as("a longer exhaustAgent_<id> handler would bypass the agent module")
                .isEmpty();
    }

    @Test
    void everyModuleIdSurvivesTheExhaustAgentButtonFormat() {
        List<String> unreachable = registeredAgentIds().stream()
                .filter(id -> !IDS_WITHOUT_AN_EXHAUST_AGENT_BUTTON.contains(id))
                .filter(id -> !AgentButtonIds.parse(AgentButtonIds.format(id, "payload"))
                        .agentId()
                        .equalsIgnoreCase(id))
                .toList();

        assertThat(unreachable).isEmpty();
    }

    private static List<String> registeredAgentIds() {
        return LeaderAgents.all().stream()
                .flatMap(module -> Stream.concat(Stream.of(module.agentId()), module.aliasIds().stream()))
                .map(id -> id.toLowerCase(Locale.ROOT))
                .toList();
    }
}
