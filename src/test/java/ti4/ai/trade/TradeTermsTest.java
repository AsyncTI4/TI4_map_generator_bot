package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class TradeTermsTest extends BaseTi4Test {

    private static final double FULL_TRUST = 1.0;

    // Sol replenishes 4 commodities, so it pays X-2; a three-commodity peer pays X-1.
    @Test
    void largeStacksPayTwo() {
        TradeTable table = TradeTable.withAiSol();
        Player naalu = table.aiSeat("naalu", "green");

        assertThat(TradeTerms.feeFor(table.game, table.nekro, table.sol, FULL_TRUST))
                .contains(2);
        assertThat(TradeTerms.feeFor(table.game, table.nekro, naalu, FULL_TRUST))
                .contains(1);
    }

    // A follower ahead of the holder, or a human the AI trusts less than 0.8, pays 2.
    @Test
    void followersAheadOrLessTrustedPayTwo() {
        TradeTable table = TradeTable.withAiSol();
        Player naalu = table.aiSeat("naalu", "green");

        assertThat(TradeTerms.feeFor(table.game, table.nekro, naalu, 0.7)).contains(2);

        table.points(naalu, 1);
        assertThat(TradeTerms.feeFor(table.game, table.nekro, naalu, FULL_TRUST))
                .contains(2);
    }

    // Nobody pays X-X: a two-commodity faction pays at most 1.
    @Test
    void twoCommodityFactionsPayAtMostOne() {
        TradeTable table = TradeTable.withAiSol();
        Player letnev = table.aiSeat("letnev", "red");
        table.points(letnev, 1);

        assertThat(TradeTerms.feeFor(table.game, table.nekro, letnev, 0.6)).contains(1);
    }

    // Hacan follows Trade for free by its own ability and owes nothing; it is not excluded either.
    @Test
    void hacanOwesNothing() {
        TradeTable table = TradeTable.withAiSol();
        Player hacan = table.aiSeat("hacan", "red");

        assertThat(TradeTerms.feeFor(table.game, table.nekro, hacan, FULL_TRUST))
                .isEmpty();
        assertThat(TradeTerms.exclusionReason(table.game, table.nekro, hacan, FULL_TRUST))
                .isEmpty();
    }

    @Test
    void excludesAPlayerAboutToWin() {
        TradeTable table = TradeTable.withAiSol();
        table.points(table.sol, 8);

        assertThat(TradeTerms.feeFor(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEmpty();
        assertThat(TradeTerms.exclusionReason(table.game, table.nekro, table.sol, FULL_TRUST))
                .contains("close to winning");
    }

    // 3 points ahead as the sole leader is rivalry 0.6.
    @Test
    void excludesTheLeader() {
        TradeTable table = TradeTable.withAiSol();
        table.points(table.sol, 3);

        assertThat(TradeTerms.exclusionReason(table.game, table.nekro, table.sol, FULL_TRUST))
                .contains("leads");
    }

    @Test
    void excludesPlayersWhoDoNotPay() {
        TradeTable table = TradeTable.withHumanSol();

        assertThat(TradeTerms.exclusionReason(table.game, table.nekro, table.sol, 0.4))
                .contains("unpaid debts");
        assertThat(TradeTerms.feeFor(table.game, table.nekro, table.sol, 0.5)).contains(2);
    }

    // Sol is 1 point ahead and sole leader (rivalry 0.4) and 2 trade goods short of Negotiate Trade Routes: its X-2 =
    // 2 trade goods from the wash would let it score, so it gets no free follow. Behind the holder it would.
    @Test
    void excludesAFollowerTheWashWouldLetScore() {
        TradeTable table = TradeTable.withAiSol();
        table.solHome();
        table.reveal("trade_routes");
        table.stock(table.sol, 0, 3);
        table.points(table.sol, 1);

        assertThat(TradeTerms.exclusionReason(table.game, table.nekro, table.sol, FULL_TRUST))
                .contains("it would let them score");

        table.points(table.nekro, 2);
        assertThat(TradeTerms.feeFor(table.game, table.nekro, table.sol, FULL_TRUST))
                .contains(2);
    }

    // With six other players the announcement still fits in one Discord message.
    @Test
    void announcementFitsInOneMessage() {
        TradeTable table = TradeTable.withAiSol();
        Player hacan = table.test.addSeat("7100000100000001", "hacan", "red");
        Player letnev = table.test.addSeat("7100000100000002", "letnev", "green");
        Player xxcha = table.test.addSeat("7100000100000003", "xxcha", "yellow");
        Player jolnar = table.test.addSeat("7100000100000004", "jolnar", "purple");
        Player yin = table.test.addSeat("7100000100000005", "yin", "orange");
        table.points(xxcha, 8);
        Map<Player, Optional<Integer>> terms = new LinkedHashMap<>();
        for (Player follower : new Player[] {table.sol, hacan, letnev, xxcha, jolnar, yin}) {
            terms.put(follower, TradeTerms.feeFor(table.game, table.nekro, follower, FULL_TRUST));
        }

        String text = TradeTerms.announcement(table.game, table.nekro, terms, follower -> FULL_TRUST);

        assertThat(text).hasSizeLessThan(TradeTerms.MAX_ANNOUNCEMENT);
        assertThat(text)
                .contains("plays **Trade**")
                .contains("(X−2)")
                .contains("(X−1)")
                .contains("Not offered: ")
                .contains("(close to winning)")
                .contains("follows free anyway; I'll offer it an even wash.")
                .contains("Pressing **Replenish Commodities** accepts these terms");
        assertThat(terms.get(hacan)).isEmpty();
        assertThat(terms.get(xxcha)).isEmpty();
        assertThat(terms.get(letnev)).contains(1);
    }

    @Test
    void termsRoundTrip() {
        Map<String, Optional<Integer>> terms = new LinkedHashMap<>();
        terms.put("sol", Optional.of(2));
        terms.put("letnev", Optional.of(1));
        terms.put("xxcha", Optional.empty());

        assertThat(TradeTerms.decode(TradeTerms.encode(terms))).isEqualTo(terms);
        assertThat(TradeTerms.decode("")).isEmpty();
        assertThat(TradeTerms.decode(null)).isEmpty();
    }
}
