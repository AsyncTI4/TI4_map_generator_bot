package ti4.discord.interactions.buttons.handlers.faction.pok.cabal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class CabalButtonHandlerTest extends BaseTi4Test {

    @Test
    void aCaptureStepReadsAProjectPiVictimAndTheCopyFlag() {
        AgentModuleFixture fixture = new AgentModuleFixture("cabal");

        assertThat(CabalButtonHandler.parseCapture(fixture.game, "cabalAgentCapture_destroyer_pi_hacan~cc"))
                .hasValueSatisfying(capture -> {
                    assertThat(capture.unit()).isEqualTo("destroyer");
                    assertThat(capture.victim()).isSameAs(fixture.target);
                    assertThat(capture.viaYssaril()).isTrue();
                });
        assertThat(CabalButtonHandler.parseCapture(fixture.game, "cabalAgentCapture_fighter_blue"))
                .hasValueSatisfying(capture -> assertThat(capture.viaYssaril()).isFalse());
        assertThat(CabalButtonHandler.parseCapture(fixture.game, "cabalAgentCapture_fighter_nobody"))
                .isEmpty();
    }
}
