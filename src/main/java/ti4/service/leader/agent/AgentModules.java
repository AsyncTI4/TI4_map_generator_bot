package ti4.service.leader.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.service.leader.agent.modules.MentakAgent;
import ti4.service.leader.agent.modules.SardakkAgent;
import ti4.service.leader.agent.modules.WinnuAgent;
import ti4.service.leader.agent.modules.homebrew.AugersAgent;
import ti4.service.leader.agent.modules.homebrew.BentorAgent;
import ti4.service.leader.agent.modules.homebrew.CymiaeAgent;
import ti4.service.leader.agent.modules.homebrew.GledgeAgent;
import ti4.service.leader.agent.modules.homebrew.KhraskAgent;
import ti4.service.leader.agent.modules.homebrew.KortaliAgent;
import ti4.service.leader.agent.modules.homebrew.KyroAgent;
import ti4.service.leader.agent.modules.homebrew.MirvedaAgent;
import ti4.service.leader.agent.modules.homebrew.NokarAgent;
import ti4.service.leader.agent.modules.homebrew.VadenAgent;
import ti4.service.leader.agent.modules.homebrew.VaylerianAgent;
import ti4.service.leader.agent.modules.homebrew.VeldyrAgent;
import ti4.service.leader.agent.modules.homebrew.ZelianAgent;
import ti4.service.leader.agent.modules.twilightsfall.HyperAgent;

@UtilityClass
class AgentModules {

    static final List<AgentModule<?>> ALL = List.of(
            new AugersAgent(),
            new SardakkAgent(),
            new BentorAgent(),
            new CymiaeAgent(),
            new GledgeAgent(),
            new HyperAgent(),
            new KhraskAgent(),
            new KortaliAgent(),
            new KyroAgent(),
            new MentakAgent(),
            new MirvedaAgent(),
            new NokarAgent(),
            new VadenAgent(),
            new VaylerianAgent(),
            new VeldyrAgent(),
            new WinnuAgent(),
            new ZelianAgent());

    private static final Map<String, AgentModule<?>> BY_ID = indexById(ALL);

    static Optional<AgentModule<?>> find(String agentId) {
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
