package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ExpectedTradeGoods;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class TradeAndPoliticsPickValueTest extends BaseTi4Test {

    // The Trade holder makes one trade good from each partner that sells its commodities at X-1. Hacan follows Trade
    // for free with Masters of Trade, so it never needs to sell to the holder.
    @Test
    void tradeCountsAWashPartnerForEachOtherSeatButHacan() {
        AiTestGame test = new AiTestGame();
        Player hacan = test.addSeat("200000000000000008", "hacan", "green");
        hacan.addAbility(ExpectedTradeGoods.MASTERS_OF_TRADE);
        test.sol.setCommoditiesTotal(4);
        hacan.setCommoditiesTotal(6);

        assertThat(TradeUses.washPartners(test.game, test.nekro)).isEqualTo(1);
    }

    // With nobody picking first the seat keeps its best card.
    @Test
    void nobodyPickingFirstLeavesTheBestCard() {
        assertThat(PoliticsUses.expectedBestLeft(List.of(9.0, 5.0, 1.0), 0)).isEqualTo(9.0);
    }

    // One of three cards taken at random: the best survives two times in three, otherwise the second best is left.
    @Test
    void oneEarlierPickerLeavesTheBestCardTwoTimesInThree() {
        assertThat(PoliticsUses.expectedBestLeft(List.of(9.0, 5.0, 1.0), 1))
                .isCloseTo(2.0 / 3 * 9 + 1.0 / 3 * 5, within(1e-9));
    }

    @Test
    void everyCardTakenLeavesNothing() {
        assertThat(PoliticsUses.expectedBestLeft(List.of(9.0, 5.0), 2)).isZero();
    }
}
