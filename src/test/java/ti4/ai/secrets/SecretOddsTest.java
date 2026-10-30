package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class SecretOddsTest extends BaseTi4Test {

    private static final int DECK_SIZE = 40;
    private static final String GATHER_A_MIGHTY_FLEET = "gamf";
    private static final int FLEET = 5;

    private AiTestGame test;
    private Player hacan;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        hacan = test.addSeat("200000000000000002", "hacan", "yellow");
    }

    private void fleet(Player player, String position) {
        Tile tile = test.place("46", position);
        test.units(tile, "space", player, UnitType.Dreadnought, FLEET);
    }

    @Test
    void theUnseenPoolIsTheDeckLessTheSecretsInTheViewersHand() {
        assertThat(SecretOdds.unseenPool(test.game, test.nekro)).hasSize(DECK_SIZE);

        test.nekro.setSecret("dhw");

        assertThat(SecretOdds.unseenPool(test.game, test.nekro))
                .hasSize(DECK_SIZE - 1)
                .doesNotContain("dhw");
        assertThat(SecretOdds.unseenPool(test.game, test.sol)).contains("dhw");
    }

    // A scored secret is public, so every player can rule it out.
    @Test
    void theUnseenPoolLosesEverySecretAnyoneHasScored() {
        test.sol.setSecretScored("pe");
        hacan.setSecretScored("mtm");

        assertThat(SecretOdds.unseenPool(test.game, test.nekro))
                .hasSize(DECK_SIZE - 2)
                .doesNotContain("pe", "mtm");
    }

    @Test
    void aPlayerWithNoUnscoredSecretHasNoChance() {
        fleet(test.sol, "302");

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.sol)).isZero();
    }

    // Five dreadnoughts on the board meet Gather a Mighty Fleet, which is still in the deck.
    @Test
    void aPlayerWhoVisiblyMeetsASecretStillInThePoolHasAHigherChance() {
        fleet(test.sol, "302");
        test.sol.setSecret("dhw");
        hacan.setSecret("dhw");

        double meets = SecretOdds.chanceReady(test.game, test.nekro, test.sol);
        double meetsNothing = SecretOdds.chanceReady(test.game, test.nekro, hacan);

        assertThat(meets).isGreaterThan(meetsNothing);
        assertThat(meetsNothing).isGreaterThan(0);
    }

    // Once Gather a Mighty Fleet has been scored by someone, nobody can be holding it.
    @Test
    void aScoredSecretNoLongerCountsForTheOtherPlayers() {
        fleet(test.sol, "302");
        test.sol.setSecret("dhw");
        double before = SecretOdds.chanceReady(test.game, test.nekro, test.sol);

        hacan.setSecretScored(GATHER_A_MIGHTY_FLEET);

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.sol)).isLessThan(before);
    }

    @Test
    void moreSecretsInHandMeanMoreChances() {
        fleet(test.sol, "302");
        test.sol.setSecret("dhw");
        double one = SecretOdds.chanceReady(test.game, test.nekro, test.sol);

        test.sol.setSecret("pe");

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.sol)).isGreaterThan(one);
    }

    // The viewer knows its own hand: a secret it holds and visibly meets is certain.
    @Test
    void theViewerKnowsItsOwnSecrets() {
        fleet(test.nekro, "302");
        test.nekro.setSecret(GATHER_A_MIGHTY_FLEET);

        assertThat(SecretOdds.chanceReady(test.game, test.nekro, test.nekro)).isCloseTo(1, within(1e-9));
    }

    @Test
    void neverLooksInsideAnotherPlayersHand() {
        test.sol.setSecret("dhw");
        test.sol.setActionCard("sabo1", 1);
        test.sol.setPromissoryNote("blue_ta", 17);
        Player spy = Mockito.spy(test.sol);
        test.game.getPlayers().put(spy.getUserID(), spy);

        SecretOdds.chanceReady(test.game, test.nekro, spy);
        SecretOdds.unseenPool(test.game, test.nekro);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }
}
