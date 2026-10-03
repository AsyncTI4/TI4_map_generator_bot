package ti4.discord.interactions.buttons.handlers.combat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CancelGroundHitsButtonIdTest {

    // Buttons posted before the planet segment existed stay clickable in Discord for months.
    @Test
    void parsesLegacyIdWithoutPlanet() {
        CancelGroundHitsButtonId id = CancelGroundHitsButtonId.parse("cancelGroundHits_302_3");

        assertThat(id.tilePosition()).isEqualTo("302");
        assertThat(id.hits()).isEqualTo(3);
        assertThat(id.planet()).isNull();
        assertThat(id.hasPlanet()).isFalse();
        assertThat(id.interlocking()).isFalse();
    }

    @Test
    void parsesLegacyInterlockingIdWithoutPlanet() {
        CancelGroundHitsButtonId id = CancelGroundHitsButtonId.parse("cancelGroundHits_302_2_interlocking");

        assertThat(id.tilePosition()).isEqualTo("302");
        assertThat(id.hits()).isEqualTo(2);
        assertThat(id.planet()).isNull();
        assertThat(id.interlocking()).isTrue();
    }

    @Test
    void parsesIdWithPlanet() {
        CancelGroundHitsButtonId id = CancelGroundHitsButtonId.parse("cancelGroundHits_302_3_mecatolrex");

        assertThat(id.tilePosition()).isEqualTo("302");
        assertThat(id.hits()).isEqualTo(3);
        assertThat(id.planet()).isEqualTo("mecatolrex");
        assertThat(id.hasPlanet()).isTrue();
        assertThat(id.interlocking()).isFalse();
    }

    @Test
    void parsesInterlockingIdWithPlanet() {
        CancelGroundHitsButtonId id =
                CancelGroundHitsButtonId.parse("cancelGroundHits_302_1_custodiavigiliaplus_interlocking");

        assertThat(id.tilePosition()).isEqualTo("302");
        assertThat(id.hits()).isEqualTo(1);
        assertThat(id.planet()).isEqualTo("custodiavigiliaplus");
        assertThat(id.interlocking()).isTrue();
    }

    // The router normally strips the faction checker, but parsing must not depend on that.
    @Test
    void ignoresFactionCheckerPrefix() {
        CancelGroundHitsButtonId id = CancelGroundHitsButtonId.parse("FFCC_sol_cancelGroundHits_302_2_jord");

        assertThat(id.tilePosition()).isEqualTo("302");
        assertThat(id.hits()).isEqualTo(2);
        assertThat(id.planet()).isEqualTo("jord");
    }

    @Test
    void buildsIdsThatRoundTrip() {
        assertThat(CancelGroundHitsButtonId.of("302", 3, "mecatolrex")).isEqualTo("cancelGroundHits_302_3_mecatolrex");
        assertThat(CancelGroundHitsButtonId.interlockingOf("302", 1, "jord"))
                .isEqualTo("cancelGroundHits_302_1_jord_interlocking");
        assertThat(CancelGroundHitsButtonId.parse(CancelGroundHitsButtonId.interlockingOf("302", 1, "jord")))
                .isEqualTo(new CancelGroundHitsButtonId("302", 1, "jord", true));
    }

    // When the planet can't be resolved the id falls back to the legacy form, so the next press re-resolves it.
    @Test
    void buildsLegacyFormWhenPlanetUnknown() {
        assertThat(CancelGroundHitsButtonId.of("302", 2, null)).isEqualTo("cancelGroundHits_302_2");
        assertThat(CancelGroundHitsButtonId.interlockingOf("302", 2, null))
                .isEqualTo("cancelGroundHits_302_2_interlocking");
    }

    @Test
    void longestRealisticIdFitsDiscordCustomIdLimit() {
        String factionChecker = "FFCC_" + "a".repeat(24) + "_";
        String id = factionChecker + CancelGroundHitsButtonId.interlockingOf("tl123", 12, "custodiavigiliaplus");

        assertThat(id.length()).isLessThanOrEqualTo(100);
    }
}
