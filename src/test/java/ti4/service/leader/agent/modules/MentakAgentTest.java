package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class MentakAgentTest extends BaseTi4Test {

    @Test
    void mentakDrawsForBothPlayers() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        int userBefore = fixture.user.getAcCount();
        int targetBefore = fixture.target.getAcCount();

        new MentakAgent().resolve(fixture.use(new MentakAgent(), fixture.target));

        assertThat(fixture.user.getAcCount()).isEqualTo(userBefore + 1);
        assertThat(fixture.target.getAcCount()).isEqualTo(targetBefore + 1);
    }
}
