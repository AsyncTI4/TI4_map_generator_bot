package ti4.service.leader.agent.modules.homebrew.bluereverie;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import org.junit.jupiter.api.Test;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class BlueReverieAgentsTest extends BaseTi4Test {

    @Test
    void everyBlueReverieAgentResolvesThroughAModule() {
        for (String id :
                new String[] {"xinagent", "kaltrimagent", "qhetagent", "belkoseaagent", "atokeraagent", "edynagent_br"
                }) {
            assertThat(LeaderAgents.isRegistered(id)).as(id).isTrue();
        }
    }

    @Test
    void offersAreOwnedByTheirHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("xin");

        assertOwnedAgentButton(XinAgent.button(fixture.user), fixture.user, "exhaustAgent_xinagent");
        assertOwnedBy(QhetAgent.button(fixture.user), fixture.user);
        assertOwnedBy(BelkoseaAgent.button(fixture.user), fixture.user);
        assertOwnedAgentButton(AtokeraAgent.button(fixture.user), fixture.user, "exhaustAgent_atokeraagent");
        assertOwnedAgentButton(
                EdynBrAgent.coexistenceButton(fixture.user, fixture.target, "lodor"),
                fixture.user,
                "useEdynAgentBr_" + AgentModuleFixture.TARGET_FACTION + "|lodor");
    }

    @Test
    void xinPicksAPlayerThenGivesThemTheSecondaryButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("xin");
        XinAgent xin = new XinAgent();

        assertThat(fixture.playerPicker(xin)).isNotNull();
        assertThat(xin.resolve(fixture.use(xin, fixture.playerPicker(xin))).messages())
                .singleElement()
                .satisfies(message -> assertThat(message.buttons()).isNotEmpty());
    }

    @Test
    void kaltrimDecodesATargetAndEdynDecodesALandingWithAProjectPiFaction() {
        AgentModuleFixture fixture = new AgentModuleFixture("kaltrim");

        fixture.assertDecodesTargets(new KaltrimAgent());
        assertThat(new EdynBrAgent().decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|lodor"))
                .contains(new EdynBrAgent.Landing(fixture.target, "lodor"));
        assertThat(new EdynBrAgent().decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .isEmpty();
    }

    @Test
    void atokeraAndQhetCannotBeUsedOutOfTheirWindows() {
        AgentModuleFixture fixture = new AgentModuleFixture("atokera");

        assertThat(new AtokeraAgent().unusableReason(fixture.game, fixture.user, fixture.user))
                .hasValueSatisfying(reason -> assertThat(reason).contains("agenda phase"));
        assertThat(new QhetAgent().decode(fixture.game, fixture.user, "")).isEmpty();
    }
}
