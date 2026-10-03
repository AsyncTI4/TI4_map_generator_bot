package ti4.discord.interactions.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class ComponentIdEnvelopeTest extends BaseTi4Test {

    @Test
    void plainIdPassesThroughUnchanged() {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode("showGameAgain");

        assertThat(envelope.ownerFaction()).isNull();
        assertThat(envelope.spoofedFaction()).isNull();
        assertThat(envelope.deleteButton()).isFalse();
        assertThat(envelope.deleteMessage()).isFalse();
        assertThat(envelope.handlerId()).isEqualTo("showGameAgain");
    }

    @Test
    void ownerPrefixIsStripped() {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode("FFCC_hacan_pillage_red_unchecked");

        assertThat(envelope.ownerFaction()).isEqualTo("hacan");
        assertThat(envelope.handlerId()).isEqualTo("pillage_red_unchecked");
    }

    @Test
    void ownerFactionContainingUnderscoreIsReadWhole() {
        // "pi_hacan" is a faction id; splitting on the first underscore would give owner "pi" and handler "hacan_..."
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode("FFCC_pi_hacan_exhaustAgent_hacanagent");

        assertThat(envelope.ownerFaction()).isEqualTo("pi_hacan");
        assertThat(envelope.handlerId()).isEqualTo("exhaustAgent_hacanagent");
    }

    @Test
    void spoofedFactionContainingUnderscoreIsReadWhole() {
        ComponentIdEnvelope envelope =
                ComponentIdEnvelope.decode("dummyPlayerSpoofpi_arborec_autoAssignGroundHits_x_2");

        assertThat(envelope.ownerFaction()).isNull();
        assertThat(envelope.spoofedFaction()).isEqualTo("pi_arborec");
        assertThat(envelope.handlerId()).isEqualTo("autoAssignGroundHits_x_2");
    }

    @Test
    void unknownOwnerFallsBackToFirstSegment() {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode("FFCC_notafaction_doThing_1");

        assertThat(envelope.ownerFaction()).isEqualTo("notafaction");
        assertThat(envelope.handlerId()).isEqualTo("doThing_1");
    }

    @Test
    void ownerPrefixWithoutPayloadDecodesToEmptyHandlerId() {
        ComponentIdEnvelope envelope = ComponentIdEnvelope.decode("FFCC_hacan");

        assertThat(envelope.ownerFaction()).isEqualTo("hacan");
        assertThat(envelope.handlerId()).isEmpty();
    }

    @Test
    void deleteMarkersAreFlaggedAndRemoved() {
        ComponentIdEnvelope envelope =
                ComponentIdEnvelope.decode("FFCC_hacan_redistributeCCButtons_deleteThisButtondeleteThisMessage");

        assertThat(envelope.deleteButton()).isTrue();
        assertThat(envelope.deleteMessage()).isTrue();
        assertThat(envelope.handlerId()).isEqualTo("redistributeCCButtons_");
    }

    @Test
    void builtPrefixesRoundTrip() {
        assertThat(ComponentIdEnvelope.decode(ComponentIdEnvelope.ownedBy("pi_keleresm") + "gain_CC")
                        .ownerFaction())
                .isEqualTo("pi_keleresm");
        assertThat(ComponentIdEnvelope.decode(ComponentIdEnvelope.spoofedAs("nekro") + "gain_CC")
                        .spoofedFaction())
                .isEqualTo("nekro");
    }
}
