package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.service.leader.agent.modules.homebrew.VaylerianAgent.DrawFor;
import ti4.service.leader.agent.modules.homebrew.VaylerianAgent.PickTarget;
import ti4.testUtils.BaseTi4Test;

class HomebrewAgentButtonsAndDecodingTest extends BaseTi4Test {

    @Test
    void everyTargetedHomebrewAgentDecodesFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        List<TargetedAgent> targeted = List.of(
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
                new VeldyrAgent(),
                new ZelianAgent());

        targeted.forEach(fixture::assertDecodesTargets);
    }

    @Test
    void vaylerianWithoutATargetAsksForOne() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaylerian");
        VaylerianAgent vaylerian = new VaylerianAgent();

        assertThat(vaylerian.decode(fixture.game, fixture.user, "")).contains(new PickTarget());
        assertThat(vaylerian.decode(fixture.game, fixture.user, "pi_hacan")).contains(new DrawFor(fixture.target));
        assertThat(vaylerian.decode(fixture.game, fixture.user, "nobody")).isEmpty();
    }

    @Test
    void buttonsAreOwnedByTheHolderAndKeepTheLegacyHandlerIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        Player holder = fixture.target;
        Player other = fixture.user;

        assertOwnedAgentButton(CymiaeAgent.button(holder), holder, "exhaustAgent_cymiaeagent_pi_hacan");
        assertOwnedAgentButton(GledgeAgent.button(holder), holder, "exhaustAgent_gledgeagent_pi_hacan");
        assertOwnedAgentButton(KhraskAgent.button(holder), holder, "exhaustAgent_khraskagent_pi_hacan");
        assertOwnedAgentButton(VeldyrAgent.button(holder), holder, "exhaustAgent_veldyragent_pi_hacan");
        assertOwnedAgentButton(VaylerianAgent.pickTargetButton(holder), holder, "exhaustAgent_vaylerianagent");
        assertOwnedAgentButton(VaylerianAgent.button(holder), holder, "exhaustAgent_vaylerianagent_pi_hacan");
        assertOwnedAgentButton(NokarAgent.button(holder), holder, "exhaustAgent_nokaragent_pi_hacan");
        assertOwnedAgentButton(ZelianAgent.button(holder), holder, "exhaustAgent_zelianagent_pi_hacan");
        assertOwnedAgentButton(MirvedaAgent.button(holder), holder, "exhaustAgent_mirvedaagent_pi_hacan");
        assertOwnedAgentButton(VadenAgent.button(holder), holder, "exhaustAgent_vadenagent_pi_hacan");
        assertOwnedAgentButton(KaloraAgent.button(holder), holder, "exhaustAgent_kaloraagent");
        assertOwnedAgentButton(LunariumAgent.button(holder), holder, "exhaustAgent_lunariumagent");
        // The holder of these agents is not the player the agent is used on.
        assertOwnedAgentButton(KortaliAgent.button(holder, other), holder, "exhaustAgent_kortaliagent_red");
        assertOwnedAgentButton(AugersAgent.button(holder, other), holder, "exhaustAgent_augersagent_mentak");
    }

    @Test
    void aPlayerWhoOnlyHasTheYssarilCopyIsOfferedCleverClever() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");

        assertThat(CymiaeAgent.button(fixture.user).getLabel()).isEqualTo("Use Clever Clever Cymiae Agent");
        assertThat(CymiaeAgent.button(fixture.target).getLabel()).isEqualTo("Use Cymiae Agent");
    }
}
