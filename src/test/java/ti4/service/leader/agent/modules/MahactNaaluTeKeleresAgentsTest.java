package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.service.leader.LeaderAgents;
import ti4.testUtils.BaseTi4Test;

class MahactNaaluTeKeleresAgentsTest extends BaseTi4Test {

    @Test
    void mahactOffersAnOwnedButtonThatNamesTheStrategyCardAndRoutesThroughTheLifecycle() {
        AgentModuleFixture fixture = new AgentModuleFixture("mahact");

        assertOwnedAgentButton(MahactAgent.followButton(fixture.user, 4), fixture.user, "exhaustAgent_mahactagent_4");
        assertThat(LeaderAgents.findRegistered(fixture.game, "mahactagent")).containsInstanceOf(MahactAgent.class);
    }

    @Test
    void mahactDecodesTheStrategyCardNumber() {
        AgentModuleFixture fixture = new AgentModuleFixture("mahact");
        MahactAgent mahact = new MahactAgent();

        assertThat(mahact.decode(fixture.game, fixture.user, "4")).contains(4);
        assertThat(mahact.decode(fixture.game, fixture.user, "")).isEmpty();
        assertThat(mahact.decode(fixture.game, fixture.user, "construction")).isEmpty();
    }

    @Test
    void naaluTeDecodesTheSystemAndTheTokenOwnerEvenWithAProjectPiFaction() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");
        NaaluTeAgent naalu = new NaaluTeAgent();

        assertThat(naalu.decode(fixture.game, fixture.user, "101_" + AgentModuleFixture.TARGET_FACTION))
                .contains(new NaaluTeAgent.Removal(fixture.tile, fixture.target));
        assertThat(naalu.decode(fixture.game, fixture.user, "999_" + AgentModuleFixture.TARGET_FACTION))
                .isEmpty();
        assertThat(LeaderAgents.findRegistered(fixture.game, "naaluagent-te")).containsInstanceOf(NaaluTeAgent.class);
    }

    @Test
    void naaluTeAnnouncesWhichTokenItRemoves() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");
        NaaluTeAgent naalu = new NaaluTeAgent();

        assertThat(naalu.exhaustAnnouncementAfterName(
                        fixture.use(naalu, new NaaluTeAgent.Removal(fixture.tile, fixture.target))))
                .contains("remove the just-placed command token");
    }

    @Test
    void keleresResolvesWhenTheHolderSpendsTheirOwnCommodities() {
        AgentModuleFixture fixture = new AgentModuleFixture("keleresm");
        KeleresAgent keleres = new KeleresAgent();

        assertThat(keleres.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(keleres.exhaustAnnouncementAfterName(fixture.use(keleres, fixture.user)))
                .endsWith("to spend commodities as if they were trade goods.");
        assertThat(keleres.exhaustAnnouncementAfterName(fixture.use(keleres, fixture.target)))
                .contains("to target");

        keleres.resolve(fixture.use(keleres, fixture.user));

        assertThat(fixture.game.getStoredValue("keleresAgentTarget")).isEmpty();
    }
}
