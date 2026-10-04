package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModules;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.leader.agent.modules.VaylerianAgent.DrawFor;
import ti4.service.leader.agent.modules.VaylerianAgent.PickTarget;
import ti4.testUtils.BaseTi4Test;

class TargetedAgentDecodingTest extends BaseTi4Test {

    @Test
    void everyTargetedAgentDecodesFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        List<TargetedAgent> targeted = AgentModules.ALL.stream()
                .filter(TargetedAgent.class::isInstance)
                .map(TargetedAgent.class::cast)
                .toList();

        assertThat(targeted).hasSizeGreaterThanOrEqualTo(13);
        for (TargetedAgent module : targeted) {
            Player user = fixture.user;
            assertThat(module.decode(fixture.game, user, AgentModuleFixture.TARGET_FACTION))
                    .as(module.agentId() + " with a pi_ faction")
                    .contains(fixture.target);
            assertThat(module.decode(fixture.game, user, AgentModuleFixture.TARGET_COLOR))
                    .as(module.agentId() + " with a colour")
                    .contains(fixture.target);
            assertThat(module.decode(fixture.game, user, ""))
                    .as(module.agentId() + " without a target")
                    .isEmpty();
            assertThat(module.decode(fixture.game, user, "nobody"))
                    .as(module.agentId() + " with an unknown target")
                    .isEmpty();
        }
    }

    @Test
    void winnuDefaultsToTheUser() {
        AgentModuleFixture fixture = new AgentModuleFixture("winnu");
        WinnuAgent winnu = new WinnuAgent();

        assertThat(winnu.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(winnu.decode(fixture.game, fixture.user, "pi_hacan")).contains(fixture.target);
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
    void buildersKeepTheLegacyWireShape() {
        // Buttons already posted in Discord use these exact strings, so the format must not drift.
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        Player target = fixture.target;

        assertThat(CymiaeAgent.buttonId(target)).isEqualTo("exhaustAgent_cymiaeagent_pi_hacan");
        assertThat(MentakAgent.buttonId(target)).isEqualTo("exhaustAgent_mentakagent_pi_hacan");
        assertThat(GledgeAgent.buttonId(target)).isEqualTo("exhaustAgent_gledgeagent_pi_hacan");
        assertThat(KhraskAgent.buttonId(target)).isEqualTo("exhaustAgent_khraskagent_pi_hacan");
        assertThat(VeldyrAgent.buttonId(target)).isEqualTo("exhaustAgent_veldyragent_pi_hacan");
        assertThat(WinnuAgent.buttonId()).isEqualTo("exhaustAgent_winnuagent");
        assertThat(VaylerianAgent.buttonId()).isEqualTo("exhaustAgent_vaylerianagent");
        assertThat(VaylerianAgent.buttonId(target)).isEqualTo("exhaustAgent_vaylerianagent_pi_hacan");
        assertThat(VadenAgent.buttonId(target)).isEqualTo("exhaustAgent_vadenagent_pi_hacan");
        assertThat(KortaliAgent.buttonId(target)).isEqualTo("exhaustAgent_kortaliagent_blue");
        assertThat(NokarAgent.buttonId(target)).isEqualTo("exhaustAgent_nokaragent_pi_hacan");
        assertThat(ZelianAgent.buttonId(target)).isEqualTo("exhaustAgent_zelianagent_pi_hacan");
        assertThat(MirvedaAgent.buttonId(target)).isEqualTo("exhaustAgent_mirvedaagent_pi_hacan");
    }
}
