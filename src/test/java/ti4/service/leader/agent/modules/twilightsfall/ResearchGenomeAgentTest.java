package ti4.service.leader.agent.modules.twilightsfall;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class ResearchGenomeAgentTest extends BaseTi4Test {

    @Test
    void addsThreeCardsToTheUsersDraft() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        ResearchGenomeAgent research = new ResearchGenomeAgent();

        research.resolve(fixture.use(research, fixture.user));

        assertThat(fixture.game.getStoredValue(ResearchGenomeAgent.SPLICE_KEY_PREFIX + fixture.user.getFaction()))
                .isEqualTo("Yes");
        assertThat(research.exhaustAnnouncementAfterName(fixture.use(research, fixture.user)))
                .endsWith("has used the _Research Genome_ to add 3 cards to the draft.");
    }

    @Test
    void buttonIsOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");

        assertOwnedAgentButton(ResearchGenomeAgent.button(fixture.user), fixture.user, "exhaustAgent_researchagent");
    }
}
