package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class DihmohnAgentTest extends BaseTi4Test {

    private static final String LODOR = "lodor";

    @Test
    void thePayloadIsAPlanetOnTheMap() {
        AgentModuleFixture fixture = new AgentModuleFixture("dihmohn");
        DihmohnAgent dihmohn = new DihmohnAgent();

        assertThat(dihmohn.decode(fixture.game, fixture.user, LODOR)).contains(LODOR);
        assertThat(dihmohn.decode(fixture.game, fixture.user, "nowhere")).isEmpty();
        assertThat(dihmohn.decode(fixture.game, fixture.user, "")).isEmpty();
        assertOwnedAgentButton(
                DihmohnAgent.button(fixture.user, LODOR, "Lodor"), fixture.user, "exhaustAgent_dihmohnagent_" + LODOR);
    }

    @Test
    void landsOneExtraInfantryOnThePlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("dihmohn");
        DihmohnAgent dihmohn = new DihmohnAgent();

        AgentOutcome outcome = dihmohn.resolve(fixture.use(dihmohn, LODOR));

        assertThat(fixture.tile.getUnitHolders().get(LODOR).getUnitCount(UnitType.Infantry, fixture.user.getColor()))
                .isEqualTo(1);
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.text()).contains("Jgin Faru, the Dih-Mohn agent");
        });
    }
}
