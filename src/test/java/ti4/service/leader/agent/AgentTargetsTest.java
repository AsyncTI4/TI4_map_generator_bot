package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class AgentTargetsTest extends BaseTi4Test {

    @Test
    void aFactionContainingTheSeparatorIsReadWhole() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");

        assertThat(AgentTargets.player(fixture.game, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
        // A trailing payload segment after the faction is ignored.
        assertThat(AgentTargets.player(fixture.game, "blue_extra")).contains(fixture.target);
    }

    @Test
    void anEmptyPayloadMeansTheUserOnlyWhenTheTargetIsOptional() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");

        assertThat(AgentTargets.player(fixture.game, "")).isEmpty();
        assertThat(AgentTargets.playerOrUser(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(AgentTargets.playerOrUser(fixture.game, fixture.user, "nobody"))
                .isEmpty();
    }
}
