package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.helpers.ButtonHelper;
import ti4.testUtils.BaseTi4Test;

class SpyNetworkTest extends BaseTi4Test {

    // Politics draws 2 action cards.
    private static final int POLITICS_DRAW = 2;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
    }

    private void holdCards(int cards) {
        for (int card = 1; card <= cards; card++) test.nekro.setActionCard("sabo" + card, card);
    }

    @Test
    void isNotWithinReachWithoutTheSecret() {
        holdCards(4);

        assertThat(SpyNetwork.holds(test.nekro)).isFalse();
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isFalse();
    }

    // Form a Spy Network discards 5 action cards: with 3 or 4 in hand, Politics' 2 cards complete it, with 2 they do
    // not.
    @Test
    void isWithinReachWhenTheDrawCompletesFiveCards() {
        test.nekro.setSecret(SpyNetwork.ID);

        holdCards(2);
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isFalse();
        assertThat(SpyNetwork.needsCards(test.game, test.nekro)).isTrue();

        holdCards(3);
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isTrue();

        holdCards(4);
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isTrue();
    }

    // With 5 cards already in hand the secret needs nothing more.
    @Test
    void isNotWithinReachOnceFiveCardsAreHeld() {
        test.nekro.setSecret(SpyNetwork.ID);
        holdCards(5);

        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isFalse();
        assertThat(SpyNetwork.needsCards(test.game, test.nekro)).isFalse();
    }

    // Under Sanctions the hand limit is 3, so 5 cards can never be held at once and the secret is out of reach.
    @Test
    void isNeverWithinReachBelowAFiveCardHandLimit() {
        test.nekro.setSecret(SpyNetwork.ID);
        holdCards(3);
        test.game.addLaw("sanctions", null);

        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isFalse();
        assertThat(SpyNetwork.needsCards(test.game, test.nekro)).isFalse();
    }

    // The Absol Sanctions leave the elected player a hand limit of exactly 5: enough for the secret, so 3 or 4 cards
    // put it within reach and 5 complete it.
    @Test
    void isWithinReachWithAHandLimitOfExactlyFive() {
        test.nekro.setSecret(SpyNetwork.ID);
        test.game.addLaw("absol_sanctions", test.nekro.getFaction());
        assertThat(ButtonHelper.getACLimit(test.game, test.nekro)).isEqualTo(SpyNetwork.CARDS);

        holdCards(3);
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isTrue();

        holdCards(4);
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isTrue();

        holdCards(5);
        assertThat(SpyNetwork.needsCards(test.game, test.nekro)).isFalse();
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isFalse();
    }

    // A scored Spy Network is no longer held.
    @Test
    void doesNotCountAScoredSpyNetwork() {
        test.nekro.setSecretScored(SpyNetwork.ID);
        holdCards(4);

        assertThat(SpyNetwork.holds(test.nekro)).isFalse();
        assertThat(SpyNetwork.withinReach(test.game, test.nekro, POLITICS_DRAW)).isFalse();
    }
}
