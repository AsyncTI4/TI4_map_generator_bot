package ti4.service.leader;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.PlainExhaustAgent;
import ti4.service.leader.agent.modules.OfficialAgents;
import ti4.service.leader.agent.modules.homebrew.HomebrewAgents;
import ti4.service.leader.genome.LeaningGenome;
import ti4.service.leader.genome.TwilightsFallGenomes;
import ti4.service.leader.genome.homebrew.HomebrewGenomes;
import ti4.service.leader.genome.homebrew.tknova.TkNovaGenomes;

@UtilityClass
public class LeaderAgents {

    private static final List<AgentCatalog> AGENT_CATALOGS =
            List.of(OfficialAgents.CATALOG, HomebrewAgents.CATALOG, TkNovaGenomes.CATALOG);

    private static final List<AgentCatalog> TWILIGHTS_FALL_GENOME_CATALOGS =
            List.of(TwilightsFallGenomes.CATALOG, HomebrewGenomes.CATALOG);

    private static final List<AgentModule<?>> AGENTS = modulesOf(AGENT_CATALOGS);

    private static final Map<String, AgentModule<?>> AGENTS_BY_ID = indexById(AGENTS);

    private static final String LEADER_USED_TO_TELL_IF_LEADERS_ARE_LOADED = "arborecagent";

    private static volatile Map<String, AgentModule<?>> twilightsFallGenomesById;

    public static List<AgentModule<?>> all() {
        return Stream.concat(AGENTS.stream(), twilightsFallGenomes().values().stream())
                .toList();
    }

    public static AgentModule<?> find(Game game, String agentId) {
        return findRegistered(game, agentId).orElseGet(() -> fallbackFor(game, agentId));
    }

    public static Optional<AgentModule<?>> findRegistered(Game game, String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        String key = agentId.toLowerCase(Locale.ROOT);
        if (game != null && game.isTwilightsFallMode() && twilightsFallGenomes().containsKey(key)) {
            return Optional.of(twilightsFallGenomes().get(key));
        }
        return Optional.ofNullable(AGENTS_BY_ID.get(key));
    }

    public static boolean isRegistered(String agentId) {
        return findAgent(agentId).isPresent()
                || (agentId != null && twilightsFallGenomes().containsKey(agentId.toLowerCase(Locale.ROOT)));
    }

    public static Optional<AgentModule<?>> findAgent(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(AGENTS_BY_ID.get(agentId.toLowerCase(Locale.ROOT)));
    }

    private static AgentModule<?> fallbackFor(Game game, String agentId) {
        PlainExhaustAgent plain = PlainExhaustAgent.forLeader(agentId);
        LeaderModel leader = Mapper.getLeader(agentId);
        if (leader == null || game == null) {
            return plain;
        }
        String gameName = leader.getName(game);
        return gameName.equals(leader.getName()) ? plain : LeaningGenome.leaningOn(agentId, gameName, plain);
    }

    private static Map<String, AgentModule<?>> twilightsFallGenomes() {
        Map<String, AgentModule<?>> index = twilightsFallGenomesById;
        if (index != null) {
            return index;
        }
        index = indexById(modulesOf(TWILIGHTS_FALL_GENOME_CATALOGS));
        if (Mapper.getLeader(LEADER_USED_TO_TELL_IF_LEADERS_ARE_LOADED) != null) {
            twilightsFallGenomesById = index;
        }
        return index;
    }

    private static List<AgentModule<?>> modulesOf(List<AgentCatalog> catalogs) {
        return catalogs.stream().flatMap(catalog -> catalog.modules().stream()).toList();
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
