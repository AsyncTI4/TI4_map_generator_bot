package ti4.service.leader.genome.homebrew.tknova;

import java.util.List;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.genome.ValiantGenome;

public final class TkNovaGenomes implements AgentCatalog {

    public static final TkNovaGenomes CATALOG = new TkNovaGenomes();

    private static final List<AgentModule<?>> MODULES = List.of(
            new DeploymentGenome(),
            new SwarmGenome(),
            new SplittingGenome(),
            new MirrorGenome(),
            new ValiantGenome("tknovavaliantagent", "Valiant Genome", 2),
            new InvestmentGenome(),
            new RecursiveGenome(),
            new ScornfulGenome(),
            new AristocraticGenome(),
            new HumanGenome(),
            new BrutalGenome(),
            new RavenousGenome(),
            new ActionGenome());

    private TkNovaGenomes() {}

    @Override
    public List<AgentModule<?>> modules() {
        return MODULES;
    }
}
