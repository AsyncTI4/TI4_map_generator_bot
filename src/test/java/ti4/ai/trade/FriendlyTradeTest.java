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

// Friendly trade (the user's design, 2026-11-15/16): with a player in good relations the AI flips that player's
// commodities for free when only one side has commodities, and as Trade holder charges X-1 whatever the follower's
// commodity value, unless the trade goods could let that player win. Relations start where the AI's personality puts
// them (STOOGE max, FLOATER good, NEUTRAL, WARRIOR bad, WARLORD worse; there is no floor) and move when a partner
// charges or floats it.
class FriendlyTradeTest extends BaseTi4Test {

    private static final double FULL_TRUST = 1.0;
    private static final long NOW = AiTestGame.NOW;
    private static final int TRADE = 5;

    private static TradeTable floaterTable() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        AiPersonality.set(table.nekro, AiPersonality.FLOATER);
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
    void theDefaultPersonalityIsNeutral() {
        TradeTable table = TradeTable.withAiSol();
        assertThat(AiPersonality.of(table.nekro)).isEqualTo(AiPersonality.NEUTRAL);
        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isZero();
        AiPersonality.set(table.nekro, AiPersonality.FLOATER);
        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(Relations.GOOD);
    }

    // Sol has 3 commodities and Nekro has none: Nekro takes them for 3 of its trade goods, charging nothing.
    @Test
    void flipsAFriendsCommoditiesForFree() {
        TradeTable table = floaterTable();
        table.stock(table.nekro, 0, 5);
        table.stock(table.sol, 3, 0);
        AiTurnContext context = nekroTurn(table);

        assertThat(FriendlyTrade.acceptsFlip(context, table.sol, flip(3, 3))).isTrue();
        assertThat(FriendlyTrade.acceptsFlip(context, table.sol, flip(3, 2))).isTrue();
        assertThat(FriendlyTrade.acceptsFlip(context, table.sol, flip(3, 4))).isFalse();
    }

    @Test
    void aNeutralSeatDoesNotFlipForFree() {
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
        TradeTable table = floaterTable();
        table.stock(table.nekro, 0, 5);
        table.stock(table.sol, 3, 0);

        assertThat(FriendlyTrade.offerFlip(nekroTurn(table), List.of(table.sol)))
                .isPresent();
    }

    // Sol replenishes 4 commodities: a neutral holder charges X-2, a FLOATER holder X-1.
    @Test
    void chargesXMinusOneWhateverTheStack() {
        TradeTable standard = TradeTable.withAiSol();
        assertThat(TradeTerms.feeFor(standard.game, standard.nekro, standard.sol, FULL_TRUST))
                .contains(2);

        TradeTable friendly = floaterTable();
        assertThat(TradeTerms.feeFor(friendly.game, friendly.nekro, friendly.sol, FULL_TRUST))
                .contains(1);
    }

    // A follower ahead of the holder still pays only X-1 in good relations.
    @Test
    void aFollowerAheadStillPaysXMinusOne() {
        TradeTable table = floaterTable();
        Player naalu = table.aiSeat("naalu", "green");
        table.points(naalu, 1);

        assertThat(TradeTerms.feeFor(table.game, table.nekro, naalu, FULL_TRUST))
                .contains(1);
    }

    // A human the AI trusts less than 0.8 is not in good relations, so the standard X-2 applies.
    @Test
    void lowTrustIsNotGoodRelations() {
        TradeTable table = floaterTable();
        Player naalu = table.aiSeat("naalu", "green");

        assertThat(TradeTerms.feeFor(table.game, table.nekro, naalu, 0.7)).contains(2);
        assertThat(Relations.good(table.game, table.nekro, naalu, 0.7)).isFalse();
    }

    // ---- relations move: seats in good relations float each other, and stop floating players who charge them -----

    /** Sol (an AI) played Trade and Nekro, a 4-commodity seat in this test, followed it. */
    private static TradeTable solPlayedTrade(TradeTable table) {
        table.sol.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        table.game.setStoredValue("followedSC" + TRADE + "_" + table.game.getRound(), "_sol_nekro");
        table.nekro.setCommoditiesBase(4);
        return table;
    }

    private static Deal ourCommoditiesFor(int commodities, int tradeGoods) {
        return Deal.EMPTY
                .adjust("nekro", "sol", ItemType.COMMODITIES, commodities)
                .adjust("sol", "nekro", ItemType.TRADE_GOODS, tradeGoods);
    }

    private static boolean floatsSol(TradeTable table) {
        return FriendlyTrade.applies(table.game, table.nekro, table.sol, FULL_TRUST);
    }

    // A neutral Sol offers Nekro X-2 for its 4 commodities, so Nekro stops floating Sol: no free flip, and X-2 back.
    @Test
    void aHolderOfferingXMinusTwoIsNoLongerFloated() {
        TradeTable table = solPlayedTrade(floaterTable());
        TradeCardRules.followChoice(nekroTurn(table), table.sol);

        assertThat(floatsSol(table)).isFalse();
        table.sol.setCommoditiesBase(4);
        assertThat(TradeTerms.feeFor(table.game, table.nekro, table.sol, FULL_TRUST))
                .contains(2);
    }

    // Two FLOATER seats: Sol charges X-1, so Nekro keeps floating it.
    @Test
    void aFloaterHolderKeepsTheFloat() {
        TradeTable table = solPlayedTrade(floaterTable());
        AiPersonality.set(table.sol, AiPersonality.FLOATER);
        TradeCardRules.followChoice(nekroTurn(table), table.sol);

        assertThat(floatsSol(table)).isTrue();
    }

    // Nekro found 2 commodities on an explore; Sol offers 1 trade good for them, charging for the flip.
    @Test
    void chargingToFlipOurCommoditiesEndsTheFloat() {
        TradeTable table = floaterTable();
        FriendlyTrade.observeOffer(nekroTurn(table), table.sol, ourCommoditiesFor(2, 1));

        assertThat(floatsSol(table)).isFalse();
    }

    // Sol flipped Nekro's commodities for free earlier (relations rise to max), so one later charge is forgiven.
    @Test
    void anEarlierFreeFlipBanksGoodwill() {
        TradeTable table = floaterTable();
        AiTurnContext context = nekroTurn(table);

        FriendlyTrade.observeOffer(context, table.sol, ourCommoditiesFor(2, 2));
        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(Relations.MAX);

        FriendlyTrade.observeOffer(context, table.sol, ourCommoditiesFor(2, 1));
        assertThat(floatsSol(table)).isTrue();
    }

    // After following Sol's Trade, a bill of X-1 is friendly and X-2 is not.
    @Test
    void aTradeBillAboveXMinusOneEndsTheFloat() {
        TradeTable friendly = solPlayedTrade(floaterTable());
        FriendlyTrade.observeOffer(nekroTurn(friendly), friendly.sol, ourCommoditiesFor(4, 3));
        assertThat(floatsSol(friendly)).isTrue();

        TradeTable charging = solPlayedTrade(floaterTable());
        FriendlyTrade.observeOffer(nekroTurn(charging), charging.sol, ourCommoditiesFor(4, 2));
        assertThat(floatsSol(charging)).isFalse();
    }

    // A neutral seat is not floating Sol, so being charged for a flip costs Sol nothing more.
    @Test
    void aSeatNotFloatingAPartnerDoesNotSourOnACharge() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        FriendlyTrade.observeOffer(nekroTurn(table), table.sol, ourCommoditiesFor(2, 1));

        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isZero();
    }

    // A neutral Nekro starts floating Sol once Sol flips its commodities for free.
    @Test
    void aFreeFlipWinsANeutralSeatOver() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        assertThat(floatsSol(table)).isFalse();

        FriendlyTrade.observeOffer(nekroTurn(table), table.sol, ourCommoditiesFor(2, 2));
        assertThat(floatsSol(table)).isTrue();
    }

    // A FLOATER Sol charges a neutral Nekro X-1 on a 4-commodity stack, better than the X-2 it would expect.
    @Test
    void xMinusOneOnABigStackWinsANeutralSeatOver() {
        TradeTable table = solPlayedTrade(TradeTable.withAiSol());
        table.nekroAndSolNeighbour();
        AiPersonality.set(table.sol, AiPersonality.FLOATER);
        TradeCardRules.followChoice(nekroTurn(table), table.sol);

        assertThat(floatsSol(table)).isTrue();
    }

    // A STOOGE starts at max relations, so one charge still leaves Sol in good relations.
    @Test
    void aStoogeForgivesOneCharge() {
        TradeTable table = floaterTable();
        AiPersonality.set(table.nekro, AiPersonality.STOOGE);
        FriendlyTrade.observeOffer(nekroTurn(table), table.sol, ourCommoditiesFor(2, 1));

        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(Relations.GOOD);
        assertThat(floatsSol(table)).isTrue();
    }

    // WARRIOR and WARLORD start below neutral and never float a stranger.
    @Test
    void warriorsAndWarlordsStartInBadRelations() {
        TradeTable table = TradeTable.withAiSol();
        AiPersonality.set(table.nekro, AiPersonality.WARRIOR);
        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(-1);
        AiPersonality.set(table.nekro, AiPersonality.WARLORD);
        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(-2);
        assertThat(floatsSol(table)).isFalse();
    }

    // Relations have no floor: a WARLORD whose shifts with Sol sank by 5 sits at -7 (forever war).
    @Test
    void relationsHaveNoFloor() {
        TradeTable table = TradeTable.withAiSol();
        AiPersonality.set(table.nekro, AiPersonality.WARLORD);
        table.nekro.setStoredValue(Relations.KEY, "sol:-5");

        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(-7);
    }

    // Sol's attacks have cost Nekro 6 TG of value: a FLOATER Nekro sinks 3 points to -2 and stops floating Sol.
    @Test
    void attacksSinkRelations() {
        TradeTable table = floaterTable();
        ti4.ai.Hostility.add(table.game, "sol", "nekro", 6.0);
        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(-2);
        assertThat(floatsSol(table)).isFalse();
    }

    // Charging for a flip costs the seat what a boat floater would not have charged: Sol takes 2 TG off Nekro's 3
    // commodities, so even a neutral Nekro now counts 2 TG against Sol (relations -1). The same offer seen again
    // is not charged twice.
    @Test
    void aFlipFeeCostsWhatItTook() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        AiTurnContext context = nekroTurn(table);
        FriendlyTrade.observeOffer(context, table.sol, ourCommoditiesFor(3, 1));
        FriendlyTrade.observeOffer(context, table.sol, ourCommoditiesFor(3, 1));

        assertThat(ti4.ai.Hostility.amount(table.game, table.nekro, table.sol)).isEqualTo(2.0);
        assertThat(Relations.level(table.game, table.nekro, table.sol, FULL_TRUST))
                .isEqualTo(-1);
    }
}
