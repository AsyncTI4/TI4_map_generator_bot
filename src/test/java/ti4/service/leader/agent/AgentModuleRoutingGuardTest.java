package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.discord.interactions.routing.AnnotationHandlerTestAccess;
import ti4.discord.interactions.routing.ButtonHandler;

/**
 * Guards the hand-over from the legacy exhaustAgent if-chain to agent modules.
 *
 * <p>AgentLifecycle runs before the legacy chain, so a migrated agent's old branch can never run - it would just
 * rot as dead code that people keep editing. And because button routing picks the longest matching prefix, a
 * {@code @ButtonHandler("exhaustAgent_<id>")} would steal the press from the module entirely (Kalora and Lunarium
 * do this today). Both mistakes are silent at runtime, so they are caught here.
 */
class AgentModuleRoutingGuardTest {

    private static final Path LEGACY_HANDLER =
            Path.of("src", "main", "java", "ti4", "helpers", "ButtonHelperAgents.java");

    @Test
    void migratedAgentsHaveNoBranchLeftInTheLegacyChain() throws IOException {
        String legacySource = Files.readString(LEGACY_HANDLER, StandardCharsets.UTF_8);

        List<String> leftovers = new ArrayList<>();
        for (String agentId : registeredAgentIds()) {
            if (legacySource.contains("\"" + agentId + "\".equalsIgnoreCase(agent)")) {
                leftovers.add(agentId);
            }
        }

        assertThat(leftovers)
                .as("agents with a module must not keep a branch in ButtonHelperAgents.exhaustAgent")
                .isEmpty();
    }

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

    private static List<String> registeredAgentIds() {
        return AgentModules.ALL.stream()
                .flatMap(module -> Stream.concat(Stream.of(module.agentId()), module.aliasIds().stream()))
                .map(id -> id.toLowerCase(Locale.ROOT))
                .toList();
    }
}
