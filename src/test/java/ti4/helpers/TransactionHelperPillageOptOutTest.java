package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * The "willPillageOwnTransactions" stored value is a pillager's opt-out from Pillage pings on their own
 * transactions: empty means pings are on, any other value means they are off. It must only gate Pillage.
 */
class TransactionHelperPillageOptOutTest extends BaseTi4Test {

    private Game game;
    private Player mentak;

    @BeforeEach
    void setUp() {
        game = new Game();
        mentak = game.addPlayer("mentak-user", "Mentak");
        mentak.setFaction("mentak");
        mentak.setColor("black");
        mentak.addAbility("pillage");
    }

    @Test
    void pillagerWithPingsOn_hasNotOptedOut() {
        assertThat(TransactionHelper.hasOptedOutOfOwnTransactionPillage(game, mentak))
                .isFalse();
    }

    @Test
    void pillagerWhoTurnedPingsOff_hasOptedOut() {
        game.setStoredValue("willPillageOwnTransactionsmentak", "no");

        assertThat(TransactionHelper.hasOptedOutOfOwnTransactionPillage(game, mentak))
                .isTrue();
    }

    @Test
    void playerWithoutPillage_neverCountsAsOptedOut() {
        mentak.removeAbility("pillage");
        game.setStoredValue("willPillageOwnTransactionsmentak", "no");

        assertThat(TransactionHelper.hasOptedOutOfOwnTransactionPillage(game, mentak))
                .isFalse();
    }

    @Test
    void twilightsFall_ignoresTheOptOut() {
        game.setTwilightsFallMode(true);
        game.setStoredValue("willPillageOwnTransactionsmentak", "no");

        assertThat(TransactionHelper.hasOptedOutOfOwnTransactionPillage(game, mentak))
                .isFalse();
    }
}
