package ti4.service.leader.genome.homebrew.tknova;

import java.util.List;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;

public final class TkNovaGenomes implements AgentCatalog {

    public static final TkNovaGenomes CATALOG = new TkNovaGenomes();

    private static final List<AgentModule<?>> MODULES =
            List.of(new DeploymentGenome(), new SwarmGenome(), new SplittingGenome());

    private TkNovaGenomes() {}

    @Override
    public List<AgentModule<?>> modules() {
        return MODULES;
    }
}
