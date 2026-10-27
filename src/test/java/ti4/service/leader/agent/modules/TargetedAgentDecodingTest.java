package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.service.leader.agent.TargetedAgent;
import ti4.testUtils.BaseTi4Test;

class TargetedAgentDecodingTest extends BaseTi4Test {

    @Test
    void everyTargetedAgentDecodesFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        List<TargetedAgent> targeted = List.of(new HyperAgent(), new MentakAgent());

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
    void buildersKeepTheLegacyWireShape() {
        // Buttons already posted in Discord use these exact strings, so the format must not drift.
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        Player target = fixture.target;

        assertThat(MentakAgent.buttonId(target)).isEqualTo("exhaustAgent_mentakagent_pi_hacan");
        assertThat(WinnuAgent.buttonId()).isEqualTo("exhaustAgent_winnuagent");
    }
}
