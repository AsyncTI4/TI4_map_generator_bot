package ti4.service.leader.agent.modules.twilightsfall;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class PromptGenomesTest extends BaseTi4Test {

    @Test
    void experimentalGenomeDefaultsToTheUser() {
        AgentModuleFixture fixture = new AgentModuleFixture("jolnar");
        ExperimentalGenomeAgent experimental = new ExperimentalGenomeAgent();

        assertThat(experimental.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        AgentOutcome outcome = experimental.resolve(fixture.use(experimental, fixture.user));

        assertThat(outcome.messages().getFirst().text()).contains("remove infantry");
        assertThat(experimental.exhaustAnnouncement(fixture.use(experimental, fixture.user)))
                .endsWith("has exhausted the _Experimental Genome_.");
    }

    @Test
    void valiantGenomeOffersTheTargetItsDeceasedUnits() {
        AgentModuleFixture fixture = new AgentModuleFixture("bastion");
        ValiantGenomeAgent valiant = new ValiantGenomeAgent();

        fixture.assertDecodesTargets(valiant);
        AgentOutcome outcome = valiant.resolve(fixture.use(valiant, fixture.target));

        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.recipient()).isSameAs(fixture.target));
    }

    @Test
    void experimentalGenomeButtonIsOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("jolnar");

        assertOwnedAgentButton(
                ExperimentalGenomeAgent.button(fixture.user), fixture.user, "exhaustAgent_experimentalagent");
    }
}
