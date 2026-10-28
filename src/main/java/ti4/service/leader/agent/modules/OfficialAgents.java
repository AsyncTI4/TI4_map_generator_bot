package ti4.service.leader.agent.modules;

import java.util.List;
import java.util.stream.Stream;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.modules.twilightsfall.ExperimentalGenomeAgent;
import ti4.service.leader.agent.modules.twilightsfall.HyperAgent;
import ti4.service.leader.agent.modules.twilightsfall.ResearchGenomeAgent;
import ti4.service.leader.agent.modules.twilightsfall.ValiantGenomeAgent;

public final class OfficialAgents implements AgentCatalog {

    public static final OfficialAgents CATALOG = new OfficialAgents();

    private static final List<AgentModule<?>> MODULES = Stream.of(
                    List.<AgentModule<?>>of(
                            new SardakkAgent(),
                            new HyperAgent(),
                            new MentakAgent(),
                            new WinnuAgent(),
                            new ArtunoAgent(),
                            new KeleresAgent(),
                            new SolAgent(),
                            new LetnevAgent(),
                            new GhostAgent(),
                            new ObsidianAgent(),
                            new ResearchGenomeAgent(),
                            new NaazAgent(),
                            new JolNarAgent(),
                            new EmpyreanAgent(),
                            new BastionAgent(),
                            new RalNelAgent(),
                            new ExperimentalGenomeAgent(),
                            new ValiantGenomeAgent(),
                            new NekroAgent(),
                            new HacanAgent(),
                            new CabalAgent(),
                            new XxchaAgent(),
                            new RebellionAgent(),
                            new MuaatAgent(),
                            new ArborecAgent(),
                            new DeepwroughtAgent(),
                            new NaaluAgent(),
                            new YinAgent(),
                            new ArgentAgent(),
                            new MercerAgent(),
                            new L1z1xAgent()),
                    ExhaustOnlyAgents.ALL)
            .<AgentModule<?>>flatMap(List::stream)
            .toList();

    private OfficialAgents() {}

    @Override
    public List<AgentModule<?>> modules() {
        return MODULES;
    }
}
