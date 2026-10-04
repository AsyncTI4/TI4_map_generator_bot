package ti4.service.leader.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.service.leader.agent.modules.AugersAgent;
import ti4.service.leader.agent.modules.BentorAgent;
import ti4.service.leader.agent.modules.CymiaeAgent;
import ti4.service.leader.agent.modules.ExhaustOnlyAgent;
import ti4.service.leader.agent.modules.GledgeAgent;
import ti4.service.leader.agent.modules.HyperAgent;
import ti4.service.leader.agent.modules.KaloraAgent;
import ti4.service.leader.agent.modules.KhraskAgent;
import ti4.service.leader.agent.modules.KortaliAgent;
import ti4.service.leader.agent.modules.KyroAgent;
import ti4.service.leader.agent.modules.LunariumAgent;
import ti4.service.leader.agent.modules.MentakAgent;
import ti4.service.leader.agent.modules.MirvedaAgent;
import ti4.service.leader.agent.modules.NokarAgent;
import ti4.service.leader.agent.modules.SardakkAgent;
import ti4.service.leader.agent.modules.VadenAgent;
import ti4.service.leader.agent.modules.VaylerianAgent;
import ti4.service.leader.agent.modules.VeldyrAgent;
import ti4.service.leader.agent.modules.WinnuAgent;
import ti4.service.leader.agent.modules.ZelianAgent;

@UtilityClass
public class AgentModules {

    public static final List<AgentModule<?>> ALL = Stream.<AgentModule<?>>concat(
                    Stream.<AgentModule<?>>of(
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
                            new KaloraAgent(),
                            new LunariumAgent(),
                            new ZelianAgent()),
                    ExhaustOnlyAgent.ALL.stream())
            .toList();

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
