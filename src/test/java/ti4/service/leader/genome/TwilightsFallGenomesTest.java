package ti4.service.leader.genome;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.model.WormholeModel;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.service.leader.agent.modules.ArborecAgent;
import ti4.service.leader.agent.modules.GhostAgent;
import ti4.service.leader.agent.modules.YinAgent;
import ti4.testUtils.BaseTi4Test;

class TwilightsFallGenomesTest extends BaseTi4Test {

    @Test
    void theRootPicksTheGenomeInTwilightsFallGamesAndTheAgentOtherwise() {
        AgentModuleFixture fixture = new AgentModuleFixture("arborec");

        assertThat(LeaderAgents.find(fixture.game, "arborecagent")).isInstanceOf(ArborecAgent.class);

        fixture.game.setTwilightsFallMode(true);

        assertThat(LeaderAgents.find(fixture.game, "arborecagent")).isInstanceOf(Genome.class);
        assertThat(LeaderAgents.findAgent("arborecagent")).containsInstanceOf(ArborecAgent.class);
    }

    @Test
    void aDataDrivenGenomeShowsItsTwilightsFallNameAndLeansOnItsAgent() {
        AgentModuleFixture fixture = new AgentModuleFixture("arborec");
        fixture.game.setTwilightsFallMode(true);
        AgentModule<?> genome = LeaderAgents.find(fixture.game, "arborecagent");

        assertThat(genome.displayName("arborecagent")).isEqualTo("Pacific Genome");
        assertThat(AgentLifecycle.agentNameForHolder(fixture.game, fixture.user, "arborecagent"))
                .isEqualTo("the _Pacific Genome_");
        assertThat(genome).isInstanceOf(LeaningGenome.class);
        assertThat(genome.decode(fixture.game, fixture.user, "101_" + AgentModuleFixture.TARGET_FACTION))
                .isEqualTo(new ArborecAgent()
                        .decode(fixture.game, fixture.user, "101_" + AgentModuleFixture.TARGET_FACTION));
    }

    @Test
    void aGenomeLeaningOnAnAgentResolvesLikeThatAgent() {
        AgentModuleFixture fixture = new AgentModuleFixture("jolnar");
        fixture.game.setTwilightsFallMode(true);
        ExperimentalGenome experimental = new ExperimentalGenome();

        AgentOutcome outcome = experimental.resolve(fixture.use(experimental, fixture.user));

        assertThat(outcome.messages().getFirst().text()).contains("remove infantry");
    }

    @Test
    void yinGenomeOffersFightersOnly() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");
        fixture.game.setTwilightsFallMode(true);
        YinGenome yin = new YinGenome();
        YinAgent.Replacement replacement = new YinAgent.Replacement(fixture.tile, fixture.target);

        AgentOutcome outcome = yin.resolve(fixture.use(yin, replacement));

        assertThat(outcome.messages().getFirst().buttons())
                .singleElement()
                .satisfies(button -> assertThat(button.getLabel()).isEqualTo("Place 2 Fighters"));
        assertThat(YinAgent.offerButton(fixture.user, AgentModuleFixture.ACTIVE_SYSTEM)
                        .getLabel())
                .isEqualTo("Use Splitting Genome");
    }

    @Test
    void theNormalYinAgentOffersFightersAndInfantry() {
        AgentModuleFixture fixture = new AgentModuleFixture("yin");

        assertThat(YinAgent.fightersOrInfantryButtons(fixture.game, fixture.target, fixture.tile))
                .hasSizeGreaterThan(1);
    }

    @Test
    void ghostGenomeCountsADeltaWormholeInFogOfWar() {
        AgentModuleFixture fixture = new AgentModuleFixture("ghost");
        fixture.game.setFowMode(true);
        fixture.game.setTwilightsFallMode(true);
        String deltaTile = TileHelper.getAllTileModels().stream()
                .filter(tile ->
                        tile.getWormholes() != null && tile.getWormholes().contains(WormholeModel.Wormhole.DELTA))
                .map(TileModel::getId)
                .filter(id -> !"17".equals(id))
                .findFirst()
                .orElseThrow();
        fixture.game.setTile(new Tile(deltaTile, AgentModuleFixture.ACTIVE_SYSTEM));
        GhostGenome ghost = new GhostGenome();
        GhostAgent officialAgent = new GhostAgent();

        officialAgent.resolve(fixture.use(officialAgent, fixture.user));
        assertThat(fixture.game.getStoredValue(GhostAgent.ACTIVE_KEY)).isEmpty();

        ghost.resolve(fixture.use(ghost, fixture.user));

        assertThat(fixture.game.getStoredValue(GhostAgent.ACTIVE_KEY)).isEqualTo(AgentModuleFixture.ACTIVE_SYSTEM);
    }

    @Test
    void noGenomeIsCalledAnAgentInTwilightsFall() {
        Game game = new AgentModuleFixture("mentak").game;
        game.setTwilightsFallMode(true);

        for (AgentModule<?> module : LeaderAgents.all()) {
            for (String leaderId : leaderIds(module)) {
                AgentModule<?> found = LeaderAgents.find(game, leaderId);
                if (found instanceof Genome<?>) {
                    assertThat(AgentLifecycle.agentName(game, leaderId, false))
                            .as(leaderId)
                            .doesNotContain("agent");
                    assertThat(AgentLifecycle.agentName(game, leaderId, true))
                            .as(leaderId)
                            .doesNotContain("agent");
                }
            }
        }
    }

    @Test
    void everyLeaderWithATwilightsFallNameResolvesToAGenomeInTwilightsFall() {
        Game game = new AgentModuleFixture("mentak").game;
        game.setTwilightsFallMode(true);

        List<String> notGenomes = Mapper.getLeaders().values().stream()
                .filter(leader ->
                        "agent".equals(leader.getType()) && leader.getTFName().isPresent())
                .map(LeaderModel::getId)
                .filter(id -> !(LeaderAgents.find(game, id) instanceof Genome<?>))
                .toList();

        assertThat(notGenomes).isEmpty();
    }

    @Test
    void aTwilightsFallGenomeIsNotUsedInANormalGame() {
        Game game = new AgentModuleFixture("mentak").game;

        for (AgentModule<?> genome : TwilightsFallGenomes.CATALOG.modules()) {
            assertThat(LeaderAgents.find(game, genome.agentId())).isNotSameAs(genome);
        }
    }

    private static List<String> leaderIds(AgentModule<?> module) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(module.agentId()), module.aliasIds().stream())
                .toList();
    }
}
