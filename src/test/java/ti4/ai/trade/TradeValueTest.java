package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.trade.TradeValue.Valuation;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * The worked checks of the trading spec (C4.4): goal 10, round 3, nobody at 8 or more points (a point is worth 6
 * trade goods), the action phase, and peers (rivalry 0.1) unless a case says otherwise. Nekro is the AI seat S and
 * Sol the partner P; they are neighbours.
 */
class TradeValueTest extends BaseTi4Test {

    private static final double CLOSE = 1e-9;
    private static final double AI_TRUST = 1.0;
    private static final double HUMAN_TRUST = 0.8;

    private static Deal deal(String from, String to, String... raws) {
        return new Deal(List.of(raws).stream()
                .map(raw -> DealItem.parse(raw, from, to).orElseThrow())
                .toList());
    }

    private static Deal nekroSol(String... raws) {
        return deal("nekro", "sol", raws);
    }

    // Two AI peers next to each other, each with commodities to wash, and Hacan (Guild Ships: it can transact with
    // anyone) as the other outlet for both of them.
    private static TradeTable aiPeers() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        Player hacan = table.aiSeat("hacan", "red");
        table.stock(hacan, 6, 0);
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 3, 0);
        return table;
    }

    private static final Deal WASH = nekroSol("sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_Comms_3");

    // Row 1. Each side gets 3 trade goods for 3 commodities worth 0.6 to it: 3.0 - 1.8 = 1.2. The partner's gain is
    // 3 - 3 x 0.5 = 1.5, and minding a peer's gain costs 0.1 of it: 1.05 either way. Accepted and proposed.
    @Test
    void washBetweenAiPeers() {
        TradeTable table = aiPeers();

        Valuation valuation = TradeValue.of(table.game, table.nekro, table.sol, WASH, AI_TRUST);

        assertThat(valuation.selfGain()).isCloseTo(1.2, within(CLOSE));
        assertThat(valuation.partnerGain()).isCloseTo(1.5, within(CLOSE));
        assertThat(valuation.rivalry()).isCloseTo(0.1, within(CLOSE));
        assertThat(valuation.utility()).isCloseTo(1.05, within(CLOSE));
        assertThat(valuation.veto()).isEmpty();
        assertThat(TradeValue.predictedFor(table.game, table.sol, table.nekro, WASH))
                .isCloseTo(1.05, within(CLOSE));
        assertThat(TradeValue.acceptable(table.game, table.nekro, table.sol, WASH, AI_TRUST))
                .isTrue();
        assertThat(TradeValue.proposable(table.game, table.nekro, table.sol, WASH, AI_TRUST))
                .isTrue();
    }

    // Row 2. The same wash with a partner about to win (rivalry 1.0): 1.2 - 1.5 = -0.3. Refused as stingy.
    @Test
    void washWithAPartnerAboutToWin() {
        TradeTable table = aiPeers();
        table.points(table.sol, 8);

        Valuation valuation = TradeValue.of(table.game, table.nekro, table.sol, WASH, AI_TRUST);

        assertThat(valuation.rivalry()).isCloseTo(1.0, within(CLOSE));
        assertThat(valuation.utility()).isCloseTo(-0.3, within(CLOSE));
        assertThat(TradeValue.acceptable(table.game, table.nekro, table.sol, WASH, AI_TRUST))
                .isFalse();
    }

    // Row 3. Hacan holds Trade and bills Sol at X = 4, k = 2, paying with 2 of its own commodities: 4.0 - 2 x 0.6 =
    // 2.8, and Sol's gain is 2 - 4 x 0.5 = 0, so Hacan's utility is 2.8. Sol honours the terms it accepted.
    @Test
    void hacanBillsSolForTrade() {
        TradeTable table = TradeTable.withAiSol();
        Player hacan = table.aiSeat("hacan", "red");
        table.stock(hacan, 6, 3);
        table.stock(table.sol, 4, 0);
        table.stock(table.nekro, 3, 0);
        Deal bill = deal("hacan", "sol", "sendingsol_receivinghacan_Comms_4", "sendinghacan_receivingsol_Comms_2");

        Valuation valuation = TradeValue.of(table.game, hacan, table.sol, bill, AI_TRUST);

        assertThat(valuation.selfGain()).isCloseTo(2.8, within(CLOSE));
        assertThat(valuation.partnerGain()).isCloseTo(0, within(CLOSE));
        assertThat(valuation.utility()).isCloseTo(2.8, within(CLOSE));
        assertThat(TradeValue.commitmentOk(table.game, table.sol, hacan, bill)).isTrue();
    }

    // Nekro controls its home and needs 5 trade goods for Negotiate Trade Routes; it has 3, so it is 2 short. It
    // offers its 3 commodities for 2 of Sol's trade goods.
    private static TradeTable twoShort() {
        TradeTable table = TradeTable.withAiSol();
        table.test.nekroHome();
        table.presence(table.sol, TradeTable.BESIDE_NEKRO);
        Player hacan = table.aiSeat("hacan", "red");
        table.stock(hacan, 6, 0);
        table.reveal("trade_routes");
        table.stock(table.nekro, 3, 3);
        table.stock(table.sol, 0, 4);
        return table;
    }

    private static final Deal COMMODITIES_FOR_TWO =
            nekroSol("sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_2");
    private static final Deal COMMODITIES_AND_DEBT_FOR_TWO = nekroSol(
            "sendingnekro_receivingsol_Comms_3",
            "sendingnekro_receivingsol_SendDebt_2",
            "sendingsol_receivingnekro_TGs_2");

    // Row 4. Nekro: 2 - 1.8 + 6 (the point) = 6.2, Sol's gain 3 - 2 = 1.0, utility 6.1. Sol's own view: 3 - 2 = 1.0,
    // and Nekro's gain as Sol sees it is 2 - 1.5 + 6 = 6.5; 1.0 - 0.1 x 6.5 = 0.35, enough for an AI partner. Sent and
    // accepted.
    @Test
    void desperateBuyFromAnAiPeer() {
        TradeTable table = twoShort();
        assertThat(Desperation.tradeGoodsShort(table.game, table.nekro)).hasValue(2);

        Valuation valuation = TradeValue.of(table.game, table.nekro, table.sol, COMMODITIES_FOR_TWO, AI_TRUST);

        assertThat(valuation.selfGain()).isCloseTo(6.2, within(CLOSE));
        assertThat(valuation.partnerGain()).isCloseTo(1.0, within(CLOSE));
        assertThat(valuation.utility()).isCloseTo(6.1, within(CLOSE));
        assertThat(TradeValue.predictedFor(table.game, table.sol, table.nekro, COMMODITIES_FOR_TWO))
                .isCloseTo(0.35, within(CLOSE));
        assertThat(TradeValue.proposable(table.game, table.nekro, table.sol, COMMODITIES_FOR_TWO, AI_TRUST))
                .isTrue();
        assertThat(TradeValue.acceptable(table.game, table.sol, table.nekro, COMMODITIES_FOR_TWO, AI_TRUST))
                .isTrue();
    }

    // Row 5. The same, but Nekro is 1 point ahead (Hacan shares that lead), so Sol minds Nekro's gain at 0.2:
    // 1.0 - 0.2 x 6.5 = -0.3 and the first package fails. The next one adds 2 debt from Nekro: Sol's view is
    // 3 + 2 x 0.9 - 2 = 2.8 against Nekro's gain 2 - 1.5 - 1.6 + 6 = 4.9, so 2.8 - 0.98 = 1.82. Nekro's own gain is
    // 2 - 1.8 - 1.6 + 6 = 4.6; after minding Sol's gain (3 + 1.6 - 2 = 2.6, at 0.1) its utility is 4.34.
    @Test
    void desperateBuyWhileAhead() {
        TradeTable table = twoShort();
        table.points(table.nekro, 1);
        table.points(table.game.getPlayerFromColorOrFaction("hacan"), 1);

        assertThat(TradeValue.predictedFor(table.game, table.sol, table.nekro, COMMODITIES_FOR_TWO))
                .isCloseTo(-0.3, within(CLOSE));
        assertThat(TradeValue.proposable(table.game, table.nekro, table.sol, COMMODITIES_FOR_TWO, AI_TRUST))
                .isFalse();

        assertThat(TradeValue.predictedFor(table.game, table.sol, table.nekro, COMMODITIES_AND_DEBT_FOR_TWO))
                .isCloseTo(1.82, within(CLOSE));
        Valuation valuation = TradeValue.of(table.game, table.nekro, table.sol, COMMODITIES_AND_DEBT_FOR_TWO, AI_TRUST);
        assertThat(valuation.selfGain()).isCloseTo(4.6, within(CLOSE));
        assertThat(valuation.utility()).isCloseTo(4.34, within(CLOSE));
        assertThat(TradeValue.proposable(table.game, table.nekro, table.sol, COMMODITIES_AND_DEBT_FOR_TWO, AI_TRUST))
                .isTrue();
    }

    // A human asks for Nekro's 3 commodities for 2 trade goods.
    private static TradeTable humanAsks(boolean otherOutlet) {
        TradeTable table = TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        Player hacan = table.aiSeat("hacan", "red");
        table.stock(hacan, otherOutlet ? 6 : 0, 0);
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 0, 3);
        return table;
    }

    private static final Deal HUMAN_ASKS =
            nekroSol("sendingsol_receivingnekro_TGs_2", "sendingnekro_receivingsol_Comms_3");

    // Row 6. With another outlet: 2 - 1.8 = 0.2, Sol's gain 1.0, utility 0.1, short of a human's 0.5 margin. The
    // counter of 3 commodities for 3 trade goods is worth 1.2 (at least the 0.75 a counter needs) and leaves Sol
    // gaining 0.
    @Test
    void humanAsksTooMuchAndIsCountered() {
        TradeTable table = humanAsks(true);

        Valuation asked = TradeValue.of(table.game, table.nekro, table.sol, HUMAN_ASKS, HUMAN_TRUST);
        assertThat(asked.selfGain()).isCloseTo(0.2, within(CLOSE));
        assertThat(asked.partnerGain()).isCloseTo(1.0, within(CLOSE));
        assertThat(asked.utility()).isCloseTo(0.1, within(CLOSE));
        assertThat(TradeValue.acceptable(table.game, table.nekro, table.sol, HUMAN_ASKS, HUMAN_TRUST))
                .isFalse();

        Deal counter = HUMAN_ASKS.adjust("sol", "nekro", ItemType.TRADE_GOODS, 1);
        Valuation countered = TradeValue.of(table.game, table.nekro, table.sol, counter, HUMAN_TRUST);
        assertThat(countered.utility()).isCloseTo(1.2, within(CLOSE));
        assertThat(countered.utility()).isGreaterThanOrEqualTo(TradeValue.margin(table.sol) + 0.25);
        assertThat(countered.partnerGain()).isCloseTo(0, within(CLOSE));
    }

    // Row 7. Without another outlet Nekro's commodities are worth 0.3 each: 2 - 0.9 = 1.1, utility 1.0. Accepted.
    @Test
    void humanAsksAndThereIsNoOtherOutlet() {
        TradeTable table = humanAsks(false);

        Valuation asked = TradeValue.of(table.game, table.nekro, table.sol, HUMAN_ASKS, HUMAN_TRUST);

        assertThat(asked.selfGain()).isCloseTo(1.1, within(CLOSE));
        assertThat(asked.utility()).isCloseTo(1.0, within(CLOSE));
        assertThat(TradeValue.acceptable(table.game, table.nekro, table.sol, HUMAN_ASKS, HUMAN_TRUST))
                .isTrue();
    }

    @Test
    void humansGetAWiderMargin() {
        TradeTable human = TradeTable.withHumanSol();
        TradeTable ai = TradeTable.withAiSol();

        assertThat(TradeValue.margin(human.sol)).isCloseTo(0.5, within(CLOSE));
        assertThat(TradeValue.margin(ai.sol)).isCloseTo(0.3, within(CLOSE));
    }

    // Honouring terms it agreed to (or paying its own debt) ignores the margin and "feeds the leader", but never
    // hands over what it does not have or a win.
    @Test
    void commitmentsSkipTheMarginButNotCoverage() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        table.solHome();
        table.reveal("trade_routes");
        table.points(table.sol, 6);
        table.stock(table.sol, 0, 3);
        table.stock(table.nekro, 0, 2);
        Deal fee = nekroSol("sendingnekro_receivingsol_TGs_2");
        assertThat(Stinginess.veto(table.game, table.nekro, table.sol, fee)).contains("feeds the leader");

        assertThat(TradeValue.commitmentOk(table.game, table.nekro, table.sol, fee))
                .isTrue();

        table.stock(table.nekro, 0, 1);
        assertThat(TradeValue.commitmentOk(table.game, table.nekro, table.sol, fee))
                .isFalse();

        table.stock(table.nekro, 0, 2);
        table.points(table.sol, 3);
        assertThat(TradeValue.commitmentOk(table.game, table.nekro, table.sol, fee))
                .isFalse();
    }

    // The partner must be able to deliver from what the table can see: trade goods, then commodities or trade goods
    // for commodities, and debt it actually holds.
    @Test
    void coverageReadsThePartnersStock() {
        TradeTable table = TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 1, 1);

        assertThat(TradeValue.coverable(
                        table.game, table.nekro, table.sol, nekroSol("sendingsol_receivingnekro_Comms_2")))
                .isTrue();
        assertThat(TradeValue.coverable(
                        table.game, table.nekro, table.sol, nekroSol("sendingsol_receivingnekro_TGs_2")))
                .isFalse();
        assertThat(TradeValue.coverable(
                        table.game, table.nekro, table.sol, nekroSol("sendingsol_receivingnekro_ClearDebt_1")))
                .isFalse();

        table.nekro.addDebtTokens("blue", 1);
        assertThat(TradeValue.coverable(
                        table.game, table.nekro, table.sol, nekroSol("sendingnekro_receivingsol_ClearDebt_1")))
                .isTrue();
        assertThat(TradeValue.coverable(
                        table.game, table.nekro, table.sol, nekroSol("sendingnekro_receivingsol_Comms_4")))
                .isFalse();
    }

    // A human may request a note the AI does not hold; the request names it by its bare alias. The AI can only cover
    // it once the note is back in its hand.
    @Test
    void coverageNeedsTheRequestedNoteInHand() {
        TradeTable table = TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        Deal antivirus = nekroSol("sendingnekro_receivingsol_PNs_antivirus");

        assertThat(TradeValue.coverable(table.game, table.nekro, table.sol, antivirus))
                .isFalse();

        table.nekro.setPromissoryNote("antivirus", 5);
        assertThat(TradeValue.coverable(table.game, table.nekro, table.sol, antivirus))
                .isTrue();
    }
}
