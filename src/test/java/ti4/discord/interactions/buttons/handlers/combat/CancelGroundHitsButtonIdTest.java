package ti4.discord.interactions.buttons.handlers.combat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CancelGroundHitsButtonIdTest {

    @Test
    void parsesIdWithPlanet() {
        CancelGroundHitsButtonId id = CancelGroundHitsButtonId.parse("cancelGroundHits_302_3_mecatolrex");

        assertThat(id).isEqualTo(new CancelGroundHitsButtonId("302", 3, "mecatolrex", false));
    }

    @Test
    void parsesInterlockingIdWithPlanet() {
        CancelGroundHitsButtonId id =
                CancelGroundHitsButtonId.parse("cancelGroundHits_302_1_custodiavigiliaplus_interlocking");

        assertThat(id).isEqualTo(new CancelGroundHitsButtonId("302", 1, "custodiavigiliaplus", true));
    }

    // The router normally strips the faction checker, but parsing must not depend on that.
    @Test
    void ignoresFactionCheckerPrefix() {
        CancelGroundHitsButtonId id = CancelGroundHitsButtonId.parse("FFCC_sol_cancelGroundHits_302_2_jord");

        assertThat(id).isEqualTo(new CancelGroundHitsButtonId("302", 2, "jord", false));
    }

    @Test
    void buildsIdsThatRoundTrip() {
        assertThat(CancelGroundHitsButtonId.of("302", 3, "mecatolrex")).isEqualTo("cancelGroundHits_302_3_mecatolrex");
        assertThat(CancelGroundHitsButtonId.interlockingOf("302", 1, "jord"))
                .isEqualTo("cancelGroundHits_302_1_jord_interlocking");
        assertThat(CancelGroundHitsButtonId.parse(CancelGroundHitsButtonId.interlockingOf("302", 1, "jord")))
                .isEqualTo(new CancelGroundHitsButtonId("302", 1, "jord", true));
    }

    @Test
    void longestRealisticIdFitsDiscordCustomIdLimit() {
        String factionChecker = "FFCC_" + "a".repeat(24) + "_";
        String id = factionChecker + CancelGroundHitsButtonId.interlockingOf("tl123", 12, "custodiavigiliaplus");

        assertThat(id.length()).isLessThanOrEqualTo(100);
    }
}
