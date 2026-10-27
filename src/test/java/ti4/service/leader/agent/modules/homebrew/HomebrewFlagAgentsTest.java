package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class HomebrewFlagAgentsTest extends BaseTi4Test {

    @Test
    void uydaiExplainsTheManualCommand() {
        AgentModuleFixture fixture = new AgentModuleFixture("uydai");
        UydaiAgent uydai = new UydaiAgent();

        assertThat(uydai.resolve(fixture.use(uydai, fixture.user))
                        .messages()
                        .getFirst()
                        .text())
                .contains("/game place_drawable_card");
    }

    @Test
    void tnelisAlwaysResolvesForItsHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("tnelis");
        TnelisAgent tnelis = new TnelisAgent();

        assertThat(tnelis.decode(fixture.game, fixture.user, "anything")).contains(fixture.user);
        assertOwnedAgentButton(TnelisAgent.button(fixture.user), fixture.user, "exhaustAgent_tnelisagent_tnelis");
    }

    @Test
    void toldarIsRegisteredAsExhaustOnlyAndNamesItsTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("toldar");
        var toldar = HomebrewExhaustOnlyAgents.TOLDAR;
        var choice = toldar.decode(fixture.game, fixture.user, "pi_hacan").orElseThrow();

        assertThat(toldar.exhaustAnnouncementAfterName(fixture.use(toldar, choice)))
                .contains("Maertin Donaais, the Toldar agent on");
    }
}
