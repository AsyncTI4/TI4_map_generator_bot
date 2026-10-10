package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class DealTest extends BaseTi4Test {

    private static Deal deal(String... raws) {
        List<DealItem> items = new ArrayList<>();
        for (String raw : raws) items.add(DealItem.parse(raw, "nekro", "sol").orElseThrow());
        return new Deal(items);
    }

    // The engine adds up repeated items (that is how more than 20 trade goods are offered), and so does the AI. Items
    // the offerer has with a third player are not part of this deal.
    @Test
    void readsTheOfferersItemsAndSumsDuplicates() {
        AiTestGame test = new AiTestGame();
        test.addSeat("200000000000000002", "letnev", "red");
        test.nekro.addTransactionItem("sendingnekro_receivingsol_TGs_5");
        test.nekro.addTransactionItem("sendingnekro_receivingsol_TGs_5");
        test.nekro.addTransactionItem("sendingsol_receivingnekro_Comms_3");
        test.nekro.addTransactionItem("sendingnekro_receivingletnev_TGs_1");

        Deal deal = Deal.between(test.nekro, test.sol);

        assertThat(deal.items()).hasSize(3);
        assertThat(deal.total("nekro", ItemType.TRADE_GOODS)).isEqualTo(10);
        assertThat(deal.total("sol", ItemType.COMMODITIES)).isEqualTo(3);
        assertThat(Deal.between(test.sol, test.nekro).isEmpty()).isTrue();
    }

    // Debt is the bot's ledger: a deal that only moves debt skips Pillage and the neighbour rule.
    @Test
    void knowsDebtOnlyDeals() {
        assertThat(deal("sendingnekro_receivingsol_SendDebt_2", "sendingsol_receivingnekro_ClearDebt_1")
                        .isDebtOnly())
                .isTrue();
        assertThat(deal("sendingnekro_receivingsol_SendDebt_2", "sendingsol_receivingnekro_TGs_1")
                        .isDebtOnly())
                .isFalse();
    }

    @Test
    void dropsWhatItCannotJudge() {
        Deal deal = deal("sendingsol_receivingnekro_ACs_17", "sendingnekro_receivingsol_TGs_2");

        assertThat(deal.hasUnsupported()).isTrue();
        assertThat(deal.withoutUnsupported().hasUnsupported()).isFalse();
        assertThat(deal.withoutUnsupported().total("nekro", ItemType.TRADE_GOODS))
                .isEqualTo(2);
    }

    // Commodities received become trade goods; commodities given are not trade goods lost.
    @Test
    void countsTradeGoodsEachSideEndsUpWith() {
        Deal deal = deal(
                "sendingsol_receivingnekro_Comms_3",
                "sendingsol_receivingnekro_TGs_2",
                "sendingnekro_receivingsol_TGs_1",
                "sendingnekro_receivingsol_Comms_4");

        assertThat(deal.netTradeGoodsTo("nekro")).isEqualTo(4);
        assertThat(deal.netTradeGoodsTo("sol")).isEqualTo(3);
    }

    // The fingerprint tells whether an offer changed behind its Accept button, whatever order the items are in.
    @Test
    void fingerprintIgnoresItemOrder() {
        Deal forwards = deal("sendingnekro_receivingsol_TGs_2", "sendingsol_receivingnekro_Comms_3");
        Deal backwards = deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_TGs_2");
        Deal different = deal("sendingsol_receivingnekro_Comms_2", "sendingnekro_receivingsol_TGs_2");

        assertThat(forwards.fingerprint()).isEqualTo(backwards.fingerprint());
        assertThat(forwards.fingerprint()).isNotEqualTo(different.fingerprint());
    }

    // Comparing a draft with what the builder holds: two presses of 5 are the same as one of 10, notes must match.
    @Test
    void comparesAmountsPerSenderAndType() {
        Deal pressedTwice = deal("sendingnekro_receivingsol_TGs_5", "sendingnekro_receivingsol_TGs_5");
        Deal target = deal("sendingnekro_receivingsol_TGs_10");

        assertThat(pressedTwice.sameAs(target)).isTrue();
        assertThat(target.sameAs(deal("sendingnekro_receivingsol_TGs_9"))).isFalse();
        assertThat(deal("sendingnekro_receivingsol_PNs_11").sameAs(deal("sendingnekro_receivingsol_PNs_12")))
                .isFalse();
        assertThat(Deal.EMPTY.sameAs(new Deal(List.of()))).isTrue();
    }

    @Test
    void adjustsAmounts() {
        Deal deal = deal("sendingnekro_receivingsol_TGs_3", "sendingsol_receivingnekro_Comms_3");

        assertThat(deal.adjust("nekro", "sol", ItemType.TRADE_GOODS, -1).total("nekro", ItemType.TRADE_GOODS))
                .isEqualTo(2);
        assertThat(deal.adjust("sol", "nekro", ItemType.TRADE_GOODS, 2).total("sol", ItemType.TRADE_GOODS))
                .isEqualTo(2);
        Deal without = deal.adjust("nekro", "sol", ItemType.TRADE_GOODS, -3);
        assertThat(without.items()).hasSize(1);
        assertThatThrownBy(() -> deal.adjust("nekro", "sol", ItemType.PROMISSORY, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsNotesPerSender() {
        Deal deal = deal("sendingnekro_receivingsol_PNs_11", "sendingsol_receivingnekro_TGs_1");

        assertThat(deal.notes("nekro")).containsExactly("11");
        assertThat(deal.notes("sol")).isEmpty();
        assertThat(deal.withoutNotes("nekro").notes("nekro")).isEmpty();
        assertThat(deal.with(new DealItem("sol", "nekro", ItemType.TRADE_GOODS, "2"))
                        .total("sol", ItemType.TRADE_GOODS))
                .isEqualTo(3);
    }

    @Test
    void encodesForMemory() {
        Deal deal = deal(
                "sendingnekro_receivingsol_TGs_5",
                "sendingsol_receivingnekro_SendDebt_2",
                "sendingnekro_receivingsol_PNs_11");

        assertThat(Deal.decode(deal.encode())).contains(deal);
        assertThat(Deal.decode("")).contains(Deal.EMPTY);
        assertThat(Deal.decode("nekro>sol:TGs:5;rubbish")).isEmpty();
    }
}
