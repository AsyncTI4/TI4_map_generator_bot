package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
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
    void buildersOwnTheButtonAndKeepTheLegacyWireShapeUnderneath() {
        // Buttons already posted in Discord use the bare ids, so the part after the owner prefix must not drift.
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        Player owner = fixture.user;
        Player target = fixture.target;

        assertOwnedLegacyId(CymiaeAgent.buttonId(owner, target), "exhaustAgent_cymiaeagent_pi_hacan");
        assertOwnedLegacyId(MentakAgent.buttonId(owner, target), "exhaustAgent_mentakagent_pi_hacan");
        assertOwnedLegacyId(GledgeAgent.buttonId(owner, target), "exhaustAgent_gledgeagent_pi_hacan");
        assertOwnedLegacyId(KhraskAgent.buttonId(owner, target), "exhaustAgent_khraskagent_pi_hacan");
        assertOwnedLegacyId(VeldyrAgent.buttonId(owner, target), "exhaustAgent_veldyragent_pi_hacan");
        assertOwnedLegacyId(WinnuAgent.buttonId(owner), "exhaustAgent_winnuagent");
        assertOwnedLegacyId(VaylerianAgent.buttonId(owner), "exhaustAgent_vaylerianagent");
        assertOwnedLegacyId(VaylerianAgent.buttonId(owner, target), "exhaustAgent_vaylerianagent_pi_hacan");
        assertOwnedLegacyId(VadenAgent.buttonId(owner, target), "exhaustAgent_vadenagent_pi_hacan");
        assertOwnedLegacyId(KortaliAgent.buttonId(owner, target), "exhaustAgent_kortaliagent_blue");
        assertOwnedLegacyId(NokarAgent.buttonId(owner, target), "exhaustAgent_nokaragent_pi_hacan");
        assertOwnedLegacyId(ZelianAgent.buttonId(owner, target), "exhaustAgent_zelianagent_pi_hacan");
        assertOwnedLegacyId(MirvedaAgent.buttonId(owner, target), "exhaustAgent_mirvedaagent_pi_hacan");
        assertOwnedLegacyId(KaloraAgent.buttonId(owner), "exhaustAgent_kaloraagent");
        assertOwnedLegacyId(LunariumAgent.buttonId(owner), "exhaustAgent_lunariumagent");
    }

    private static void assertOwnedLegacyId(String id, String legacyHandlerId) {
        assertThat(id).isEqualTo("FFCC_mentak_" + legacyHandlerId);
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode(id);
        assertThat(envelope.ownerFaction()).isEqualTo("mentak");
        assertThat(envelope.handlerId()).isEqualTo(legacyHandlerId);
    }
}
