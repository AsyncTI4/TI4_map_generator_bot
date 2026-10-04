package ti4.discord.interactions.buttons.ids;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentStepIdsTest {

    @Test
    void onlyAYssarilCopyIsFlagged() {
        assertThat(AgentStepIds.flagCopy("vaylerianAgent_pi_hacan", true)).isEqualTo("vaylerianAgent_pi_hacan~cc");
        assertThat(AgentStepIds.flagCopy("vaylerianAgent_pi_hacan", false)).isEqualTo("vaylerianAgent_pi_hacan");
    }

    @Test
    void parsingStripsTheFlagAndReportsTheCopy() {
        AgentStepIds.Parsed copy = AgentStepIds.parse("vaylerianAgent_pi_hacan~cc");

        assertThat(copy.id()).isEqualTo("vaylerianAgent_pi_hacan");
        assertThat(copy.viaYssaril()).isTrue();
    }

    @Test
    void stepIdsAlreadyPostedWithoutTheFlagStillParse() {
        AgentStepIds.Parsed legacy = AgentStepIds.parse("vaylerianAgent_blue");

        assertThat(legacy.id()).isEqualTo("vaylerianAgent_blue");
        assertThat(legacy.viaYssaril()).isFalse();
    }
}
