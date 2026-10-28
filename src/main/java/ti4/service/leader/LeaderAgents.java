package ti4.service.leader;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.PlainExhaustAgent;
import ti4.service.leader.agent.modules.OfficialAgents;
import ti4.service.leader.agent.modules.homebrew.HomebrewAgents;

@UtilityClass
public class LeaderAgents {

    private static final List<AgentCatalog> AGENT_CATALOGS = List.of(OfficialAgents.CATALOG, HomebrewAgents.CATALOG);

    private static final List<AgentModule<?>> ALL = AGENT_CATALOGS.stream()
            .flatMap(catalog -> catalog.modules().stream())
            .toList();

    private static final Map<String, AgentModule<?>> AGENTS_BY_ID = indexById(ALL);

    public static List<AgentModule<?>> all() {
        return ALL;
    }

    public static AgentModule<?> find(Game game, String agentId) {
        return findRegistered(game, agentId).orElseGet(() -> PlainExhaustAgent.forLeader(agentId));
    }

    public static Optional<AgentModule<?>> findRegistered(Game game, String agentId) {
        return findAgent(agentId);
    }

    public static Optional<AgentModule<?>> findAgent(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(AGENTS_BY_ID.get(agentId.toLowerCase(Locale.ROOT)));
    }

    static Map<String, AgentModule<?>> indexById(List<AgentModule<?>> modules) {
        Map<String, AgentModule<?>> byId = new HashMap<>();
        for (AgentModule<?> module : modules) {
            register(byId, module.agentId(), module);
            module.aliasIds().forEach(alias -> register(byId, alias, module));
        }
        return Map.copyOf(byId);
    }

    private static void register(Map<String, AgentModule<?>> byId, String agentId, AgentModule<?> module) {
        AgentModule<?> previous = byId.put(agentId.toLowerCase(Locale.ROOT), module);
        if (previous != null) {
            throw new IllegalStateException("Two agent modules claim " + agentId);
        }
    }
}
