package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.ExhaustOnlyAgent;
import ti4.service.leader.agent.ExhaustOnlyAgent.Choice;
import ti4.testUtils.BaseTi4Test;

class ExhaustOnlyAgentsTest extends BaseTi4Test {

    @Test
    void exhaustOnlyAgentsNeverRejectAPayload() {
        AgentModuleFixture fixture = new AgentModuleFixture("saar");

        for (ExhaustOnlyAgent agent : ExhaustOnlyAgents.ALL) {
            assertThat(agent.decode(fixture.game, fixture.user, "")).isPresent();
            assertThat(agent.decode(fixture.game, fixture.user, "garbage_payload"))
                    .isPresent();
        }
    }

    @Test
    void exhaustOnlyAgentsNameTheChosenTargetAndDoNothingElse() {
        AgentModuleFixture fixture = new AgentModuleFixture("saar");
        ExhaustOnlyAgent saar = ExhaustOnlyAgents.ALL.getFirst();
        Choice onTarget = saar.decode(fixture.game, fixture.user, "pi_hacan").orElseThrow();

        assertThat(saar.resolve(fixture.use(saar, onTarget)).messages()).isEmpty();
        assertThat(saar.exhaustAnnouncementAfterName(fixture.use(saar, onTarget)))
                .contains("has exhausted Captain Mendosa, the Saar agent on")
                .endsWith(fixture.target.getRepresentationNoPing() + ".");
    }
}
