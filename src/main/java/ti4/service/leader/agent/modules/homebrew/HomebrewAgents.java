package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.stream.Stream;
import ti4.service.leader.agent.AgentCatalog;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.modules.homebrew.beans.NatauAgent;
import ti4.service.leader.agent.modules.homebrew.bluereverie.AtokeraAgent;
import ti4.service.leader.agent.modules.homebrew.bluereverie.BelkoseaAgent;
import ti4.service.leader.agent.modules.homebrew.bluereverie.EdynBrAgent;
import ti4.service.leader.agent.modules.homebrew.bluereverie.KaltrimAgent;
import ti4.service.leader.agent.modules.homebrew.bluereverie.QhetAgent;
import ti4.service.leader.agent.modules.homebrew.bluereverie.XinAgent;

public final class HomebrewAgents implements AgentCatalog {

    public static final HomebrewAgents CATALOG = new HomebrewAgents();

    private static final List<AgentModule<?>> MODULES = Stream.of(
                    List.<AgentModule<?>>of(
                            new AugersAgent(),
                            new BentorAgent(),
                            new CymiaeAgent(),
                            new GledgeAgent(),
                            new KhraskAgent(),
                            new KortaliAgent(),
                            new KyroAgent(),
                            new MirvedaAgent(),
                            new NokarAgent(),
                            new VadenAgent(),
                            new VaylerianAgent(),
                            new VeldyrAgent(),
                            new KaloraAgent(),
                            new LunariumAgent(),
                            new ZelianAgent(),
                            new TnelisAgent(),
                            new UydaiAgent(),
                            new RohDhnaAgent(),
                            new LiZhoAgent(),
                            new TyrisAgent(),
                            new ZephyrionAgent(),
                            new MykoMentoriAgent(),
                            new GhotiAgent(),
                            new KolumeAgent(),
                            new AxisAgent(),
                            new MortheusAgent(),
                            new OnyxxaAgent(),
                            new IronAgent(),
                            new ArdentiaAgent(),
                            new KryxosAgent(),
                            new AeternaAgent(),
                            new VeylorAgent(),
                            new TaAgent(),
                            new VanguardAgent(),
                            new SarcosaAgent(),
                            new KolleccAgent(),
                            new PharadnAgent(),
                            new OlradinAgent(),
                            new KjalengardAgent(),
                            new DihmohnAgent(),
                            new CheiranAgent(),
                            new FreeSystemsAgent(),
                            new FlorzenAgent(),
                            new CeldauriAgent(),
                            new ZealotsAgent(),
                            new FogAllianceAgent(),
                            new XinAgent(),
                            new KaltrimAgent(),
                            new NatauAgent(),
                            new QhetAgent(),
                            new BelkoseaAgent(),
                            new AtokeraAgent(),
                            new EdynBrAgent()),
                    HomebrewExhaustOnlyAgents.ALL)
            .<AgentModule<?>>flatMap(List::stream)
            .toList();

    private HomebrewAgents() {}

    @Override
    public List<AgentModule<?>> modules() {
        return MODULES;
    }
}
