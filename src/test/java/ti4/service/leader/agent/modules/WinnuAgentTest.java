package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.testUtils.BaseTi4Test;

class WinnuAgentTest extends BaseTi4Test {

    @Test
    void winnuOnYourselfRewritesTheSpendMessage() {
        AgentModuleFixture fixture = new AgentModuleFixture("winnu");
        WinnuAgent winnu = new WinnuAgent();

        AgentOutcome outcome = winnu.resolve(fixture.use(winnu, fixture.user));

        assertThat(fixture.user.getSpentThingsThisWindow()).containsExactly("winnuagent");
        assertThat(outcome.pressedMessageEdit()).isNotBlank();
    }

    @Test
    void winnuOnSomeoneElseLeavesThePressedMessageAlone() {
        AgentModuleFixture fixture = new AgentModuleFixture("winnu");
        WinnuAgent winnu = new WinnuAgent();

        AgentOutcome outcome = winnu.resolve(fixture.use(winnu, fixture.target));

        assertThat(fixture.target.getSpentThingsThisWindow()).containsExactly("winnuagent");
        assertThat(outcome.pressedMessageEdit()).isNull();
    }

    @Test
    void winnuAnnouncesWhoItWasUsedOnLikeTheOtherSpendAgents() {
        AgentModuleFixture fixture = new AgentModuleFixture("winnu");
        WinnuAgent winnu = new WinnuAgent();

        assertThat(winnu.exhaustAnnouncement(fixture.use(winnu, fixture.target)))
                .contains("has exhausted Berekar Berekon, the Winnu agent for use on")
                .contains(fixture.target.getRepresentationNoPing());
    }
}
