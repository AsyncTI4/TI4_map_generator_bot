package ti4.discord.interactions.buttons.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.testUtils.BaseTi4Test;

// Extends BaseTi4Test so ComponentIdEnvelope can recognise multi-segment faction ids such as pi_mentak.
class AgentButtonIdsTest extends BaseTi4Test {

    @Test
    void formatsTheLegacyWireShape() {
        // Buttons already posted in Discord use these exact strings, so the format must not drift.
        assertThat(AgentButtonIds.format("hacanagent")).isEqualTo("exhaustAgent_hacanagent");
        assertThat(AgentButtonIds.format("augersagent", "pi_hacan")).isEqualTo("exhaustAgent_augersagent_pi_hacan");
        assertThat(AgentButtonIds.format("sardakkagent", "101", "lodor"))
                .isEqualTo("exhaustAgent_sardakkagent_101_lodor");
    }

    @Test
    void splitsTheAgentIdFromTheWholePayload() {
        AgentButtonIds.Parsed parsed = AgentButtonIds.parse("exhaustAgent_augersagent_pi_hacan");

        assertThat(parsed.agentId()).isEqualTo("augersagent");
        // The payload is kept whole: faction ids such as pi_hacan contain the separator.
        assertThat(parsed.payload()).isEqualTo("pi_hacan");
        assertThat(parsed.segments()).containsExactly("pi", "hacan");
    }

    @Test
    void parsesIdsWithoutAPayload() {
        AgentButtonIds.Parsed parsed = AgentButtonIds.parse("exhaustAgent_saaragent");

        assertThat(parsed.agentId()).isEqualTo("saaragent");
        assertThat(parsed.hasPayload()).isFalse();
        assertThat(parsed.segments()).isEmpty();
    }

    @Test
    void acceptsTheBareLeaderIdsUsedByInProcessCallers() {
        // ComponentActionHelper and /leaders exhaust call the handler with just the leader id.
        assertThat(AgentButtonIds.parse("researchagent").agentId()).isEqualTo("researchagent");
        assertThat(AgentButtonIds.parse("fogallianceagent").hasPayload()).isFalse();
    }

    @Test
    void keepsMultiStepPayloadsVerbatim() {
        AgentButtonIds.Parsed parsed = AgentButtonIds.parse("exhaustAgent_cabalagent_startCabalAgent_mentak");

        assertThat(parsed.agentId()).isEqualTo("cabalagent");
        assertThat(parsed.payload()).isEqualTo("startCabalAgent_mentak");
    }

    @Test
    void parsesTheIdTheHandlerReceivesAfterEnvelopeDecoding() {
        String raw = ComponentIdEnvelope.ownedBy("pi_mentak") + AgentButtonIds.format("mentakagent", "pi_hacan");

        AgentButtonIds.Parsed parsed =
                AgentButtonIds.parse(ComponentIdEnvelope.decode(raw).handlerId());

        assertThat(parsed.agentId()).isEqualTo("mentakagent");
        assertThat(parsed.payload()).isEqualTo("pi_hacan");
    }

    @Test
    void rejectsIdsWithoutAnAgent() {
        assertThatThrownBy(() -> AgentButtonIds.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentButtonIds.parse("exhaustAgent__pi_hacan"))
                .isInstanceOf(IllegalArgumentException.class);
        // A mis-decoded ownership prefix must fail loudly instead of yielding a bogus agent id.
        assertThatThrownBy(() -> AgentButtonIds.parse("mentak_exhaustAgent_mentakagent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyFormattedIdFitsDiscordsCustomIdLimit() {
        String longest = ComponentIdEnvelope.ownedBy("pi_freesystems")
                + AgentButtonIds.format("nomadagentmercer", "999", "mallicelocked")
                + "deleteThisMessage";

        assertThat(longest.length()).isLessThanOrEqualTo(100);
    }
}
