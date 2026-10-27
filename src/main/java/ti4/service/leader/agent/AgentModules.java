package ti4.service.leader.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.service.leader.agent.modules.ArborecAgent;
import ti4.service.leader.agent.modules.ArgentAgent;
import ti4.service.leader.agent.modules.ArtunoAgent;
import ti4.service.leader.agent.modules.BastionAgent;
import ti4.service.leader.agent.modules.CabalAgent;
import ti4.service.leader.agent.modules.DeepwroughtAgent;
import ti4.service.leader.agent.modules.EmpyreanAgent;
import ti4.service.leader.agent.modules.ExhaustOnlyAgents;
import ti4.service.leader.agent.modules.GhostAgent;
import ti4.service.leader.agent.modules.HacanAgent;
import ti4.service.leader.agent.modules.JolNarAgent;
import ti4.service.leader.agent.modules.KeleresAgent;
import ti4.service.leader.agent.modules.L1z1xAgent;
import ti4.service.leader.agent.modules.LetnevAgent;
import ti4.service.leader.agent.modules.MentakAgent;
import ti4.service.leader.agent.modules.MercerAgent;
import ti4.service.leader.agent.modules.MuaatAgent;
import ti4.service.leader.agent.modules.NaaluAgent;
import ti4.service.leader.agent.modules.NaazAgent;
import ti4.service.leader.agent.modules.NekroAgent;
import ti4.service.leader.agent.modules.ObsidianAgent;
import ti4.service.leader.agent.modules.RalNelAgent;
import ti4.service.leader.agent.modules.RebellionAgent;
import ti4.service.leader.agent.modules.SardakkAgent;
import ti4.service.leader.agent.modules.SolAgent;
import ti4.service.leader.agent.modules.WinnuAgent;
import ti4.service.leader.agent.modules.XxchaAgent;
import ti4.service.leader.agent.modules.YinAgent;
import ti4.service.leader.agent.modules.homebrew.AeternaAgent;
import ti4.service.leader.agent.modules.homebrew.ArdentiaAgent;
import ti4.service.leader.agent.modules.homebrew.AugersAgent;
import ti4.service.leader.agent.modules.homebrew.AxisAgent;
import ti4.service.leader.agent.modules.homebrew.BentorAgent;
import ti4.service.leader.agent.modules.homebrew.CeldauriAgent;
import ti4.service.leader.agent.modules.homebrew.CheiranAgent;
import ti4.service.leader.agent.modules.homebrew.CymiaeAgent;
import ti4.service.leader.agent.modules.homebrew.DihmohnAgent;
import ti4.service.leader.agent.modules.homebrew.FlorzenAgent;
import ti4.service.leader.agent.modules.homebrew.FogAllianceAgent;
import ti4.service.leader.agent.modules.homebrew.FreeSystemsAgent;
import ti4.service.leader.agent.modules.homebrew.GhotiAgent;
import ti4.service.leader.agent.modules.homebrew.GledgeAgent;
import ti4.service.leader.agent.modules.homebrew.HomebrewExhaustOnlyAgents;
import ti4.service.leader.agent.modules.homebrew.IronAgent;
import ti4.service.leader.agent.modules.homebrew.KaloraAgent;
import ti4.service.leader.agent.modules.homebrew.KhraskAgent;
import ti4.service.leader.agent.modules.homebrew.KjalengardAgent;
import ti4.service.leader.agent.modules.homebrew.KolleccAgent;
import ti4.service.leader.agent.modules.homebrew.KolumeAgent;
import ti4.service.leader.agent.modules.homebrew.KortaliAgent;
import ti4.service.leader.agent.modules.homebrew.KryxosAgent;
import ti4.service.leader.agent.modules.homebrew.KyroAgent;
import ti4.service.leader.agent.modules.homebrew.LiZhoAgent;
import ti4.service.leader.agent.modules.homebrew.LunariumAgent;
import ti4.service.leader.agent.modules.homebrew.MirvedaAgent;
import ti4.service.leader.agent.modules.homebrew.MortheusAgent;
import ti4.service.leader.agent.modules.homebrew.MykoMentoriAgent;
import ti4.service.leader.agent.modules.homebrew.NokarAgent;
import ti4.service.leader.agent.modules.homebrew.OlradinAgent;
import ti4.service.leader.agent.modules.homebrew.OnyxxaAgent;
import ti4.service.leader.agent.modules.homebrew.PharadnAgent;
import ti4.service.leader.agent.modules.homebrew.RohDhnaAgent;
import ti4.service.leader.agent.modules.homebrew.SarcosaAgent;
import ti4.service.leader.agent.modules.homebrew.TaAgent;
import ti4.service.leader.agent.modules.homebrew.TnelisAgent;
import ti4.service.leader.agent.modules.homebrew.TyrisAgent;
import ti4.service.leader.agent.modules.homebrew.UydaiAgent;
import ti4.service.leader.agent.modules.homebrew.VadenAgent;
import ti4.service.leader.agent.modules.homebrew.VanguardAgent;
import ti4.service.leader.agent.modules.homebrew.VaylerianAgent;
import ti4.service.leader.agent.modules.homebrew.VeldyrAgent;
import ti4.service.leader.agent.modules.homebrew.VeylorAgent;
import ti4.service.leader.agent.modules.homebrew.ZealotsAgent;
import ti4.service.leader.agent.modules.homebrew.ZelianAgent;
import ti4.service.leader.agent.modules.homebrew.ZephyrionAgent;
import ti4.service.leader.agent.modules.twilightsfall.ExperimentalGenomeAgent;
import ti4.service.leader.agent.modules.twilightsfall.HyperAgent;
import ti4.service.leader.agent.modules.twilightsfall.ResearchGenomeAgent;
import ti4.service.leader.agent.modules.twilightsfall.ValiantGenomeAgent;

@UtilityClass
class AgentModules {

    static final List<AgentModule<?>> ALL = Stream.of(
                    List.<AgentModule<?>>of(
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
                            new ZelianAgent(),
                            new ArtunoAgent(),
                            new KeleresAgent(),
                            new SolAgent(),
                            new LetnevAgent(),
                            new GhostAgent(),
                            new ObsidianAgent(),
                            new ResearchGenomeAgent(),
                            new TnelisAgent(),
                            new UydaiAgent(),
                            new NaazAgent(),
                            new JolNarAgent(),
                            new EmpyreanAgent(),
                            new BastionAgent(),
                            new RalNelAgent(),
                            new ExperimentalGenomeAgent(),
                            new ValiantGenomeAgent(),
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
                            new NekroAgent(),
                            new HacanAgent(),
                            new CabalAgent(),
                            new XxchaAgent(),
                            new RebellionAgent(),
                            new MuaatAgent(),
                            new ArborecAgent(),
                            new DeepwroughtAgent(),
                            new KolleccAgent(),
                            new PharadnAgent(),
                            new OlradinAgent(),
                            new KjalengardAgent(),
                            new NaaluAgent(),
                            new YinAgent(),
                            new ArgentAgent(),
                            new MercerAgent(),
                            new L1z1xAgent(),
                            new DihmohnAgent(),
                            new CheiranAgent(),
                            new FreeSystemsAgent(),
                            new FlorzenAgent(),
                            new CeldauriAgent(),
                            new ZealotsAgent(),
                            new FogAllianceAgent()),
                    ExhaustOnlyAgents.ALL,
                    HomebrewExhaustOnlyAgents.ALL)
            .<AgentModule<?>>flatMap(List::stream)
            .toList();

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
