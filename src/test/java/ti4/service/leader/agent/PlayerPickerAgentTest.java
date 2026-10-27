package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedStep;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.PlayerPickerAgent.Pick;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class PlayerPickerAgentTest extends BaseTi4Test {

    private static List<PlayerPickerAgent> pickers() {
        return AgentModules.ALL.stream()
                .filter(PlayerPickerAgent.class::isInstance)
                .map(PlayerPickerAgent.class::cast)
                .toList();
    }

    @Test
    void everyPickerOffersOwnedStepsFlaggedOnlyForACleverCleverCopy() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");

        assertThat(pickers()).hasSizeGreaterThanOrEqualTo(5);
        for (PlayerPickerAgent picker : pickers()) {
            for (boolean viaYssaril : List.of(false, true)) {
                AgentOutcome outcome = picker.resolve(
                        viaYssaril
                                ? fixture.useAsYssarilCopy(picker, fixture.playerPicker(picker))
                                : fixture.use(picker, fixture.playerPicker(picker)));
                assertThat(outcome.messages().getFirst().buttons())
                        .as(picker.agentId())
                        .hasSize(2)
                        .allSatisfy(button -> assertOwnedStep(button, fixture.user, viaYssaril));
            }
        }
    }

    @Test
    void aPickStepNamesTheAgentTheTargetAndTheCopy() {
        AgentModuleFixture fixture = new AgentModuleFixture("nekro");

        Pick pick = PlayerPickerAgent.parsePick(fixture.game, "agentPick_nekroagent_pi_hacan~cc")
                .orElseThrow();

        assertThat(pick.picker().agentId()).isEqualTo("nekroagent");
        assertThat(pick.target()).isSameAs(fixture.target);
        assertThat(pick.viaYssaril()).isTrue();
    }

    @Test
    void aPickStepAcceptsAColourInFogOfWar() {
        AgentModuleFixture fixture = new AgentModuleFixture("hacan");

        assertThat(PlayerPickerAgent.parsePick(fixture.game, "agentPick_hacanagent_" + AgentModuleFixture.TARGET_COLOR))
                .hasValueSatisfying(pick -> assertThat(pick.target()).isSameAs(fixture.target));
    }

    @Test
    void stepsPostedBeforeTheMigrationStillResolve() {
        AgentModuleFixture fixture = new AgentModuleFixture("nekro");

        assertThat(PlayerPickerAgent.parsePick(fixture.game, "nekroAgentRes_pi_hacan"))
                .hasValueSatisfying(pick -> {
                    assertThat(pick.picker().agentId()).isEqualTo("nekroagent");
                    assertThat(pick.target()).isSameAs(fixture.target);
                    assertThat(pick.viaYssaril()).isFalse();
                });
        assertThat(PlayerPickerAgent.parsePick(fixture.game, "vaylerianAgent_blue~cc"))
                .hasValueSatisfying(pick -> assertThat(pick.viaYssaril()).isTrue());
    }

    @Test
    void unknownPlayersAndAgentsDoNotResolve() {
        AgentModuleFixture fixture = new AgentModuleFixture("nekro");

        assertThat(PlayerPickerAgent.parsePick(fixture.game, "agentPick_nekroagent_nobody"))
                .isEmpty();
        assertThat(PlayerPickerAgent.parsePick(fixture.game, "agentPick_mentakagent_pi_hacan"))
                .isEmpty();
    }
}
