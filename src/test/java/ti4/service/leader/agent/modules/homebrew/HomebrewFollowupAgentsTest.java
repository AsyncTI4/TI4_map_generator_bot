package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.ExhaustOnlyAgent;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.service.leader.agent.modules.homebrew.VaylerianAgent.PickTarget;
import ti4.testUtils.BaseTi4Test;

class HomebrewFollowupAgentsTest extends BaseTi4Test {

    @Test
    void homebrewExhaustOnlyAgentsNeverRejectAPayload() {
        AgentModuleFixture fixture = new AgentModuleFixture("nivyn");

        for (ExhaustOnlyAgent agent : HomebrewExhaustOnlyAgents.ALL) {
            assertThat(agent.decode(fixture.game, fixture.user, "")).isPresent();
            assertThat(agent.decode(fixture.game, fixture.user, "garbage_payload"))
                    .isPresent();
        }
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

        AgentOutcome outcome = vaylerian.resolve(fixture.useAsYssarilCopy(vaylerian, new PickTarget()));

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
