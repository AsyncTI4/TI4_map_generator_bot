package ti4.service.leader.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.service.leader.agent.modules.AugersAgent;
import ti4.service.leader.agent.modules.SardakkAgent;

@UtilityClass
public class AgentModules {

    public static final List<AgentModule<?>> ALL = List.of(new AugersAgent(), new SardakkAgent());

    private static final Map<String, AgentModule<?>> BY_ID = indexById(ALL);

    public static Optional<AgentModule<?>> find(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(agentId.toLowerCase(Locale.ROOT)));
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
