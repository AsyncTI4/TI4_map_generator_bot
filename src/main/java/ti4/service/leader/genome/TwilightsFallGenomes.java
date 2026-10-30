package ti4.service.leader.genome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.PlainExhaustAgent;
import ti4.service.leader.agent.modules.OfficialAgents;

public final class TwilightsFallGenomes implements AgentCatalog {

    public static final TwilightsFallGenomes CATALOG = new TwilightsFallGenomes();

    private TwilightsFallGenomes() {}

    @Override
    public List<AgentModule<?>> modules() {
        List<AgentModule<?>> modules = new ArrayList<>(exceptions());
        Set<String> handled =
                Set.copyOf(modules.stream().map(AgentModule::agentId).toList());
        Mapper.getLeaders().values().stream()
                .filter(TwilightsFallGenomes::isOfficialAgentWithAGenomeName)
                .filter(leader -> !handled.contains(leader.getId()))
                .sorted(Comparator.comparing(LeaderModel::getId))
                .map(leader -> leaningOnItsAgent(leader.getId(), leader.getTFNameIfAble()))
                .forEach(modules::add);
        return List.copyOf(modules);
    }

    static AgentModule<?> leaningOnItsAgent(String leaderId, String genomeName) {
        AgentModule<?> agent =
                OfficialAgents.CATALOG.find(leaderId).orElseGet(() -> PlainExhaustAgent.forLeader(leaderId));
        return LeaningGenome.leaningOn(leaderId, genomeName, agent);
    }

    private static List<AgentModule<?>> exceptions() {
        return List.of(
                new YinGenome(),
                new GhostGenome(),
                new ExperimentalGenome(),
                new HyperGenome(),
                new ResearchGenome(),
                new ValiantGenome());
    }

    private static boolean isOfficialAgentWithAGenomeName(LeaderModel leader) {
        return "agent".equals(leader.getType())
                && leader.getTFName().isPresent()
                && leader.getSource() != null
                && leader.getSource().isOfficial();
    }
}
