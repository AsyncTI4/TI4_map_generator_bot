package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class BaseAgentButtonsAndDecodingTest extends BaseTi4Test {

    @Test
    void mentakDecodesFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        fixture.assertDecodesTargets(new MentakAgent());
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
        fixture.target.addLeader("sardakkagent");

        assertThat(SardakkAgent.button(fixture.user, "101", "lodor", "Lodor").getLabel())
                .isEqualTo("Use Clever Clever N'orr Agent on Lodor");
    }
}
