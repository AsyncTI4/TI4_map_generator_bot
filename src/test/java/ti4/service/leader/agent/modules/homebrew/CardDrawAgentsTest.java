package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.service.leader.agent.modules.homebrew.VaylerianAgent.DrawFor;
import ti4.service.leader.agent.modules.homebrew.VaylerianAgent.PickTarget;
import ti4.testUtils.BaseTi4Test;

class CardDrawAgentsTest extends BaseTi4Test {

    @Test
    void cymiaeDrawsOneActionCardForTheTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("cymiae");
        int before = fixture.target.getAcCount();

        AgentOutcome outcome = new CymiaeAgent().resolve(fixture.use(new CymiaeAgent(), fixture.target));

        assertThat(fixture.target.getAcCount()).isEqualTo(before + 1);
        assertThat(outcome.messages()).isEmpty();
    }

    @Test
    void vaylerianDrawsForAChosenTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaylerian");
        int before = fixture.target.getAcCount();
        VaylerianAgent vaylerian = new VaylerianAgent();

        vaylerian.resolve(fixture.use(vaylerian, new DrawFor(fixture.target)));

        assertThat(fixture.target.getAcCount()).isEqualTo(before + 1);
    }

    @Test
    void vaylerianWithoutATargetOffersAPlayerPicker() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaylerian");
        VaylerianAgent vaylerian = new VaylerianAgent();

        AgentOutcome outcome = vaylerian.resolve(fixture.use(vaylerian, new PickTarget()));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons())
                    .isNotEmpty()
                    .allSatisfy(button -> assertThat(button.getCustomId()).startsWith("vaylerianAgent_"));
        });
    }
}
