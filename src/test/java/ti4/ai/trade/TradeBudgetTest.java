package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class TradeBudgetTest extends BaseTi4Test {

    private TradeTable table;
    private Player letnev;

    // Nekro controls its home (Mordai II, 4 resources) and has 3 commodities and 6 trade goods. It has offered Sol 1
    // commodity and 2 trade goods, and Letnev 1 commodity.
    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        letnev = table.humanSeat("letnev", "red");
        table.test.nekroHome();
        table.stock(table.nekro, 3, 6);
        table.nekro.addTransactionItem("sendingnekro_receivingsol_Comms_1");
        table.nekro.addTransactionItem("sendingnekro_receivingsol_TGs_2");
        table.nekro.addTransactionItem("sendingnekro_receivingletnev_Comms_1");
    }

    @Test
    void freeStockLeavesOutWhatPendingOffersPromise() {
        assertThat(TradeBudget.promisedCommodities(table.nekro)).isEqualTo(2);
        assertThat(TradeBudget.freeCommodities(table.context())).isEqualTo(1);
        assertThat(TradeBudget.freeTradeGoods(table.game, table.nekro)).isEqualTo(4);
    }

    // Covering a deal with Sol replaces what is offered to Sol, so only the other offers are held back.
    @Test
    void spareStockForOnePartnerIgnoresThatPartnersOwnOffer() {
        assertThat(TradeBudget.spareCommodities(table.nekro, table.sol)).isEqualTo(2);
        assertThat(TradeBudget.spareTradeGoods(table.game, table.nekro, table.sol))
                .isEqualTo(6);
        assertThat(TradeBudget.spareCommodities(table.nekro, letnev)).isEqualTo(2);
        assertThat(TradeBudget.spareTradeGoods(table.game, table.nekro, letnev)).isEqualTo(4);
    }

    // Erect a Monument takes 8 resources: Mordai II pays 4 and trade goods the other 4, so those 4 are kept back.
    @Test
    void keepsTheTradeGoodsTheReservedObjectiveNeeds() {
        table.nekro.getTransactionItems().clear();
        table.reveal("monument");

        assertThat(TradeBudget.reservedTradeGoods(table.game, table.nekro)).isEqualTo(4);
        assertThat(TradeBudget.freeTradeGoods(table.game, table.nekro)).isEqualTo(2);
    }
}
