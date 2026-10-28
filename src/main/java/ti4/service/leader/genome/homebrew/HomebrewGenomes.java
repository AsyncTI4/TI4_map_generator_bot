package ti4.service.leader.genome.homebrew;

import java.util.List;
import ti4.model.Mapper;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.PlainExhaustAgent;
import ti4.service.leader.agent.modules.homebrew.HomebrewAgents;
import ti4.service.leader.genome.LeaningGenome;

public final class HomebrewGenomes implements AgentCatalog {

    public static final HomebrewGenomes CATALOG = new HomebrewGenomes();

    private static final List<String> LEADERS_WITH_A_TWILIGHTS_FALL_NAME = List.of("vaylerianagent", "yssarilagent_br");

    private HomebrewGenomes() {}

    @Override
    public List<AgentModule<?>> modules() {
        return LEADERS_WITH_A_TWILIGHTS_FALL_NAME.stream()
                .filter(leaderId -> Mapper.getLeader(leaderId) != null)
                .<AgentModule<?>>map(HomebrewGenomes::genomeFor)
                .toList();
    }

    private static AgentModule<?> genomeFor(String leaderId) {
        AgentModule<?> agent =
                HomebrewAgents.CATALOG.find(leaderId).orElseGet(() -> PlainExhaustAgent.forLeader(leaderId));
        String genomeName = Mapper.getLeader(leaderId).getTFNameIfAble();
        return LeaningGenome.leaningOn(leaderId, genomeName, agent);
    }
}
