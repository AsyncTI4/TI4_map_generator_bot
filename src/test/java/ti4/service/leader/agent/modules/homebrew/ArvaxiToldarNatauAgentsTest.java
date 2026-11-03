package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.service.leader.agent.modules.homebrew.beans.DreamAgent;
import ti4.service.leader.agent.modules.homebrew.beans.NatauAgent;
import ti4.service.leader.agent.modules.homebrew.wftv.ArvaxiAgent;
import ti4.testUtils.BaseTi4Test;

class ArvaxiToldarNatauAgentsTest extends BaseTi4Test {

    @Test
    void theAgentsAreRegisteredAndNoLongerExhaustOnly() {
        assertThat(LeaderAgents.findAgent("toldaragent")).containsInstanceOf(ToldarAgent.class);
        assertThat(LeaderAgents.findAgent("natauagent")).containsInstanceOf(NatauAgent.class);
        assertThat(LeaderAgents.findAgent("arvaxiagent")).containsInstanceOf(ArvaxiAgent.class);
    }

    @Test
    void toldarWashesTheTargetsCommoditiesAndOffersItsHolderAChoice() {
        AgentModuleFixture fixture = new AgentModuleFixture("toldar");
        fixture.target.setCommodities(2);
        fixture.target.setTg(1);
        ToldarAgent toldar = new ToldarAgent();

        AgentOutcome outcome = toldar.resolve(fixture.use(toldar, fixture.target));

        assertThat(fixture.target.getTg()).isEqualTo(3);
        assertThat(fixture.target.getCommodities()).isZero();
        assertThat(outcome.messages()).hasSize(2);
        assertThat(outcome.messages().get(0).text()).contains("2 commodities");
    }

    @Test
    void arvaxiDecodesAnActionCardIndexAndATargetWithAProjectPiFaction() {
        AgentModuleFixture fixture = new AgentModuleFixture("arvaxi");
        ArvaxiAgent arvaxi = new ArvaxiAgent();

        assertThat(arvaxi.decode(fixture.game, fixture.user, "12_" + AgentModuleFixture.TARGET_FACTION))
                .contains(new ArvaxiAgent.Retrieval(12, fixture.target));
        assertThat(arvaxi.decode(fixture.game, fixture.user, "x_" + AgentModuleFixture.TARGET_FACTION))
                .isEmpty();
        assertThat(arvaxi.unusableReason(fixture.game, fixture.user, new ArvaxiAgent.Retrieval(12, fixture.target)))
                .isPresent();
    }

    @Test
    void natauDefaultsToTheActivePlayer() {
        AgentModuleFixture fixture = new AgentModuleFixture("natau");
        NatauAgent natau = new NatauAgent();
        fixture.game.updateActivePlayer(fixture.target);

        assertThat(natau.decode(fixture.game, fixture.user, "active")).contains(fixture.target);
        assertThat(natau.decode(fixture.game, fixture.user, fixture.user.getFaction()))
                .isEmpty();
        assertOwnedAgentButton(
                NatauAgent.cardsInfoButton(fixture.user), fixture.user, "exhaustAgent_natauagent_active");
    }

    @Test
    void dreamTargetsTheActivePlayerUnlessAPlayerIsNamed() {
        AgentModuleFixture fixture = new AgentModuleFixture("dream");
        DreamAgent dream = new DreamAgent();
        fixture.game.updateActivePlayer(fixture.target);

        assertThat(LeaderAgents.findAgent("dreamagent")).containsInstanceOf(DreamAgent.class);
        assertThat(dream.decode(fixture.game, fixture.user, "")).contains(fixture.target);
        assertThat(dream.decode(fixture.game, fixture.user, fixture.user.getFaction()))
                .contains(fixture.user);
        assertOwnedAgentButton(DreamAgent.cardsInfoButton(fixture.user), fixture.user, "dream_agent_select_player");
    }
}
