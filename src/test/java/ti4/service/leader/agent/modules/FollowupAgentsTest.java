package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentModules;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.ExhaustOnlyAgent.Choice;
import ti4.service.leader.agent.modules.VaylerianAgent.PickTarget;
import ti4.testUtils.BaseTi4Test;

class FollowupAgentsTest extends BaseTi4Test {

    @Test
    void exhaustOnlyAgentsAreRegisteredAndNeverRejectAPayload() {
        AgentModuleFixture fixture = new AgentModuleFixture("saar");

        for (ExhaustOnlyAgent agent : ExhaustOnlyAgent.ALL) {
            assertThat(AgentModules.find(agent.agentId())).contains(agent);
            assertThat(agent.decode(fixture.game, fixture.user, "")).isPresent();
            assertThat(agent.decode(fixture.game, fixture.user, "garbage_payload"))
                    .isPresent();
        }
    }

    @Test
    void exhaustOnlyAgentsNameTheChosenTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("saar");
        ExhaustOnlyAgent saar =
                (ExhaustOnlyAgent) AgentModules.find("saaragent").orElseThrow();
        Choice onTarget = saar.decode(fixture.game, fixture.user, "pi_hacan").orElseThrow();

        AgentOutcome outcome = saar.resolve(fixture.use(saar, onTarget));

        assertThat(outcome.messages()).isEmpty();
        assertThat(saar.exhaustAnnouncement(fixture.use(saar, onTarget)))
                .contains("has exhausted Captain Mendosa, the Saar agent on")
                .endsWith(fixture.target.getRepresentationNoPing() + ".");
    }

    @Test
    void kaloraOffersTheUserACommandToken() {
        AgentModuleFixture fixture = new AgentModuleFixture("kalora");
        KaloraAgent kalora = new KaloraAgent();

        AgentOutcome outcome = kalora.resolve(fixture.use(kalora, fixture.user));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons()).isNotEmpty();
        });
    }

    @Test
    void lunariumAddsItsSpendModifierAndRewritesTheSpendMessage() {
        AgentModuleFixture fixture = new AgentModuleFixture("lunarium");
        LunariumAgent lunarium = new LunariumAgent();

        AgentOutcome outcome = lunarium.resolve(fixture.use(lunarium, fixture.user));

        assertThat(fixture.user.getSpentThingsThisWindow()).containsExactly("lunariumagent");
        assertThat(outcome.pressedMessageEdit()).isNotBlank();
    }

    @Test
    void aYssarilCopyOfVaylerianFlagsItsTargetButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");
        VaylerianAgent vaylerian = new VaylerianAgent();

        AgentOutcome outcome = vaylerian.resolve(fixture.use(vaylerian, new PickTarget()));

        assertThat(outcome.messages().getFirst().buttons())
                .isNotEmpty()
                .allSatisfy(button -> assertThat(button.getCustomId()).endsWith("~cc"));
    }

    @Test
    void theRealVaylerianAgentLeavesItsTargetButtonsUnflagged() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaylerian");
        VaylerianAgent vaylerian = new VaylerianAgent();

        AgentOutcome outcome = vaylerian.resolve(fixture.use(vaylerian, new PickTarget()));

        assertThat(outcome.messages().getFirst().buttons())
                .isNotEmpty()
                .noneSatisfy(button -> assertThat(button.getCustomId()).endsWith("~cc"));
    }
}
