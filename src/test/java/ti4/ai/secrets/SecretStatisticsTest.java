package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

// The completion table comes from 4,929 winners over 29,574 player-games (16.7% of seats win), counted per secret as
// "wins with the secret scored or still in hand". Dividing that by the number of games of the most-kept secret
// (about 2,783) and by the share of copies that were not discarded gives the chance that a kept copy gets scored:
// P(scored | kept) = scored / (M * (1 - discard)). A copy still unscored in hand at the end wins only 0 to 8% of the
// time for every secret, so an unscorable secret is a dead slot.
class SecretStatisticsTest extends BaseTi4Test {

    private static final List<String> BASE_AND_POK_SECRETS = List.of(
            "lsc", "hrm", "sai", "sb", "eh", "te", "fc", "syc", "fwm", "dyp", "sar", "dhw", "ctr", "mlp", "mtm", "uf",
            "ans", "btgk", "fsn", "pem", "gamf", "btv", "pe", "mew", "otf", "dfat", "faa", "dp", "mp", "fwp", "baf",
            "csl", "dtd", "mrm", "dts", "ose", "eap", "dtgs", "ttfd", "bam");

    // Fight with Precision happens to be measured at exactly the fallback's 0.5, so it is the one secret allowed to
    // look like it is missing from the table.
    @Test
    void hasACompletionRateForEveryBaseAndPokSecret() {
        assertThat(BASE_AND_POK_SECRETS).hasSize(40).doesNotHaveDuplicates();
        assertThat(BASE_AND_POK_SECRETS)
                .filteredOn(secret -> SecretStatistics.completion(secret) == SecretStatistics.UNKNOWN_COMPLETION)
                .containsExactly("fwp");
        for (String secret : BASE_AND_POK_SECRETS) {
            assertThat(SecretStatistics.completion(secret)).as(secret).isBetween(0.2, 0.9);
        }
    }

    // Status secrets are finished more reliably than action secrets: Learn the Secrets of the Cosmos (lsc) is at the
    // top, Become a Martyr (a home planet has to be lost) at the bottom.
    @Test
    void ranksTheEasiestAndHardestSecretsAtTheEnds() {
        assertThat(SecretStatistics.completion("lsc")).isGreaterThan(SecretStatistics.completion("dyp"));
        assertThat(SecretStatistics.completion("dyp")).isGreaterThan(SecretStatistics.completion("ttfd"));
        assertThat(SecretStatistics.completion("ttfd")).isGreaterThan(SecretStatistics.completion("bam"));
    }

    @Test
    void fallsBackToEvenOddsForAnythingElse() {
        assertThat(SecretStatistics.completion("survey")).isEqualTo(SecretStatistics.UNKNOWN_COMPLETION);
    }

    // The opening table is how often the AI's own copies were scored by the round-two status phase in self-play
    // (shrunk toward 15% for small samples). Every base and PoK secret has a measured share, and the quick ones
    // (Threaten Enemies, Hoard Raw Materials) sit far above the combat secrets the AI almost never finishes that early.
    @Test
    void hasAnOpeningShareForEveryBaseAndPokSecret() {
        for (String secret : BASE_AND_POK_SECRETS) {
            assertThat(SecretStatistics.openingCompletion(secret)).as(secret).isBetween(0.0, 0.7);
        }
        assertThat(SecretStatistics.openingCompletion("survey")).isEqualTo(SecretStatistics.UNKNOWN_OPENING_COMPLETION);
        assertThat(SecretStatistics.openingCompletion("te")).isGreaterThan(SecretStatistics.openingCompletion("dyp"));
        assertThat(SecretStatistics.openingCompletion("hrm")).isGreaterThan(SecretStatistics.openingCompletion("btv"));
    }
}
