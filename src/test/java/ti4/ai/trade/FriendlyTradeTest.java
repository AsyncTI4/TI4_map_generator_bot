package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiPersonality;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The user's BOAT_FLOAT personality (2026-11-15): a more trade-friendly AI. With a player in good relations (trust and
// no grudge) it flips that player's commodities for free when only one side has commodities, and as Trade holder it
// charges X-1 whatever the follower's commodity value, unless the trade goods could let that player win.
class FriendlyTradeTest extends BaseTi4Test {

    private static final double FULL_TRUST = 1.0;
    private static final long NOW = AiTestGame.NOW;

    private static TradeTable boatFloatTable() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        AiPersonality.set(table.nekro, AiPersonality.BOAT_FLOAT);
        return table;
    }

    private static AiTurnContext nekroTurn(TradeTable table) {
        table.game.setPhaseOfGame("action");
        table.test.isActive(table.nekro, "action");
        return table.test.contextFor(table.nekro, Set.of(), NOW, TradeButtons.entry("various", NOW - 60_000L));
    }

    private static Deal flip(int commodities, int tradeGoods) {
        return Deal.EMPTY
                .adjust("sol", "nekro", ItemType.COMMODITIES, commodities)
                .adjust("nekro", "sol", ItemType.TRADE_GOODS, tradeGoods);
    }

    @Test
    void theDefaultPersonalityIsStandard() {
        TradeTable table = TradeTable.withAiSol();
        assertThat(AiPersonality.of(table.nekro)).isEqualTo(AiPersonality.STANDARD);
        AiPersonality.set(table.nekro, AiPersonality.BOAT_FLOAT);
        assertThat(AiPersonality.isBoatFloat(table.nekro)).isTrue();
    }

    // Sol has 3 commodities and Nekro has none: Nekro takes them for 3 of its trade goods, charging nothing.
    @Test
    void flipsAFriendsCommoditiesForFree() {
        TradeTable table = boatFloatTable();
        table.stock(table.nekro, 0, 5);
        table.stock(table.sol, 3, 0);
        AiTurnContext context = nekroTurn(table);

        assertThat(FriendlyTrade.acceptsFlip(context, table.sol, flip(3, 3))).isTrue();
        assertThat(FriendlyTrade.acceptsFlip(context, table.sol, flip(3, 2))).isTrue();
        assertThat(FriendlyTrade.acceptsFlip(context, table.sol, flip(3, 4))).isFalse();
    }

    @Test
    void theStandardPersonalityDoesNotFlipForFree() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        table.stock(table.nekro, 0, 5);
        table.stock(table.sol, 3, 0);

        assertThat(FriendlyTrade.acceptsFlip(nekroTurn(table), table.sol, flip(3, 3)))
                .isFalse();
    }

    // On its own turn, with no commodities of its own, Nekro offers the flip to Sol.
    @Test
    void offersTheFlipOnItsTurn() {
        TradeTable table = boatFloatTable();
        table.stock(table.nekro, 0, 5);
        table.stock(table.sol, 3, 0);

        assertThat(FriendlyTrade.offerFlip(nekroTurn(table), List.of(table.sol)))
                .isPresent();
    }

    // Sol replenishes 4 commodities: a standard holder charges X-2, a BOAT_FLOAT holder X-1.
    @Test
    void chargesXMinusOneWhateverTheStack() {
        TradeTable standard = TradeTable.withAiSol();
        assertThat(TradeTerms.feeFor(standard.game, standard.nekro, standard.sol, FULL_TRUST))
                .contains(2);

        TradeTable friendly = boatFloatTable();
        assertThat(TradeTerms.feeFor(friendly.game, friendly.nekro, friendly.sol, FULL_TRUST))
                .contains(1);
    }

    // A follower ahead of the holder still pays only X-1 in good relations.
    @Test
    void aFollowerAheadStillPaysXMinusOne() {
        TradeTable table = boatFloatTable();
        Player naalu = table.aiSeat("naalu", "green");
        table.points(naalu, 1);

        assertThat(TradeTerms.feeFor(table.game, table.nekro, naalu, FULL_TRUST))
                .contains(1);
    }

    // A human the AI trusts less than 0.8 is not in good relations, so the standard X-2 applies.
    @Test
    void lowTrustIsNotGoodRelations() {
        TradeTable table = boatFloatTable();
        Player naalu = table.aiSeat("naalu", "green");

        assertThat(TradeTerms.feeFor(table.game, table.nekro, naalu, 0.7)).contains(2);
        assertThat(FriendlyTrade.goodRelations(table.game, table.nekro, naalu, 0.7))
                .isFalse();
    }
}
