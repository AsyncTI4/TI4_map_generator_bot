package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Form a Spy Network (five action cards) is a secret the footprint model does not measure, so it used to count at
// the flat base rate for everyone, even for the seat holding it with five cards in hand. With WIN_RACE_SECRETS the
// seat checks its own secrets against their real thresholds and measures rivals on the public progress the bot
// already tracks (the action card count is public).
class SecretOddsMeasuredTest extends BaseTi4Test {

    private static final String FORM_A_SPY_NETWORK = "fsn";
    private static final String[] FIVE_CARDS = {"sabo1", "sabo2", "sabo3", "sabo4", "rally"};

    private AiTestGame test;
    private Player hacan;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        hacan = test.addSeat("200000000000000002", "hacan", "yellow");
    }

    private static void fiveActionCards(Player player) {
        for (String card : FIVE_CARDS) player.setActionCard(card);
    }

    private void disableForNekro() {
        AiExperiments.disable(test.nekro, Set.of(Experiment.WIN_RACE_SECRETS));
    }

    @Test
    void theSeatKnowsItsOwnUnmodelledSecretIsReady() {
        test.nekro.setSecret(FORM_A_SPY_NETWORK);
        fiveActionCards(test.nekro);

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.nekro)).isCloseTo(1, within(1e-9));
    }

    @Test
    void withTheExperimentDisabledTheOwnUnmodelledSecretStaysAtTheBaseRate() {
        test.nekro.setSecret(FORM_A_SPY_NETWORK);
        fiveActionCards(test.nekro);
        disableForNekro();

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.nekro))
                .isCloseTo(SecretOdds.BASE_RATE, within(1e-9));
    }

    // Sol and Hacan each hold one secret; Sol visibly holds five action cards, so Spy Network in the pool is ready
    // for Sol and not for Hacan.
    @Test
    void aRivalsPublicProgressOnAnUnmodelledSecretRaisesItsChance() {
        test.sol.setSecret("dhw");
        hacan.setSecret("dhw");
        fiveActionCards(test.sol);

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.sol))
                .isGreaterThan(SecretOdds.chanceReady(test.game, test.nekro, hacan));
    }

    @Test
    void withTheExperimentDisabledUnmodelledSecretsCountTheSameForEveryRival() {
        test.sol.setSecret("dhw");
        hacan.setSecret("dhw");
        fiveActionCards(test.sol);
        disableForNekro();

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.sol))
                .isCloseTo(SecretOdds.chanceReady(test.game, test.nekro, hacan), within(1e-9));
    }
}
