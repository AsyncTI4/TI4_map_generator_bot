package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.service.leader.agent.TargetedAgent;
import ti4.testUtils.BaseTi4Test;

class TargetedAgentDecodingTest extends BaseTi4Test {

    @Test
    void everyTargetedAgentDecodesFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        List<TargetedAgent> targeted = List.of(new MentakAgent());

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
    void buttonsAreOwnedByTheHolderAndKeepTheLegacyHandlerIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        Player holder = fixture.user;

        assertOwnedAgentButton(WinnuAgent.button(holder), holder, "exhaustAgent_winnuagent");
        assertOwnedAgentButton(MentakAgent.button(holder, fixture.target), holder, "exhaustAgent_mentakagent_pi_hacan");
        assertOwnedAgentButton(
                SardakkAgent.button(holder, "101", "lodor", "Lodor"), holder, "exhaustAgent_sardakkagent_101_lodor");
    }

    @Test
    void aPlayerWhoOnlyHasTheYssarilCopyIsOfferedCleverClever() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");

        assertThat(SardakkAgent.button(fixture.user, "101", "lodor", "Lodor").getLabel())
                .isEqualTo("Use Clever Clever N'orr Agent on Lodor");
    }
}
