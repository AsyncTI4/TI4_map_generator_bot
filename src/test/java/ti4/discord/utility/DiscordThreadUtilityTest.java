package ti4.discord.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordThreadUtilityTest {

    @Test
    void shortNameIsUnchanged() {
        assertThat(DiscordThreadUtility.fitThreadName("pbd1234-round-2")).isEqualTo("pbd1234-round-2");
    }

    @Test
    void nameAtTheLimitIsUnchanged() {
        String name = "a".repeat(100);
        assertThat(DiscordThreadUtility.fitThreadName(name)).isEqualTo(name);
    }

    @Test
    void longNameIsCutToTheLimitKeepingItsPrefix() {
        // The /search command string that crashed thread creation on 2026-10-03.
        String name = "/search tiles source: pok min_num_planets: 0 max_num_planets: 0"
                + " include_hyperlanes: true with_anomaly: false";

        String fitted = DiscordThreadUtility.fitThreadName(name);

        assertThat(fitted).hasSize(100).startsWith("/search tiles source: pok").endsWith("...");
    }

    @Test
    void fittingIsIdempotentSoLookupsMatchCreatedThreads() {
        // Find-or-create callers compare an existing thread's name against the fitted name,
        // so fitting an already-fitted name must not change it again.
        String once = DiscordThreadUtility.fitThreadName("x".repeat(150));
        assertThat(DiscordThreadUtility.fitThreadName(once)).isEqualTo(once);
    }
}
