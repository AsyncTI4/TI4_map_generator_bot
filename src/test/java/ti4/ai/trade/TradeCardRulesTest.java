package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.trade.TradeCardRules.FollowChoice;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * The Trade strategy card with X-k terms. As holder, Nekro (black, the AI) announces a fee per player and bills every
 * player who replenished: their commodities for that many minus k, paid with its own commodities first, or k debt
 * when they can't transact, or k trade goods after Replenish and Wash. As follower, Nekro decides whether the terms
 * are worth following for free, and honours the bill. Goal 10, round 3, the action phase on Nekro's turn.
 */
class TradeCardRulesTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;
    private static final int TRADE = 5;
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();
    private static final double CLOSE = 1e-9;

    // ---- holder --------------------------------------------------------------------------------------------------

    /** Nekro played Trade (3 commodities and 2 trade goods after the primary); Sol (blue) is its neighbour. */
    private static TradeTable holder(boolean aiSol) {
        TradeTable table = aiSol ? TradeTable.withAiSol() : TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.nekro.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        table.stock(table.nekro, 3, 2);
        table.sol.setCommoditiesBase(4);
        table.sol.setStrategicCC(3);
        return table;
    }

    /** The engine appends every follower, the holder included, to this stored value. */
    private static void followed(TradeTable table, Player follower) {
        String key = "followedSC" + TRADE + "_" + table.game.getRound();
        table.game.setStoredValue(key, table.game.getStoredValue(key) + "_" + follower.getFaction());
    }

    private static AiTurnContext at(TradeTable table, Player seat, long now, AiPrompt... prompts) {
        return table.test.contextFor(seat, Set.of(), now, prompts);
    }

    private static AiTurnContext holderAt(TradeTable table, long now) {
        return at(table, table.nekro, now, TradeButtons.entry("various", now - MINUTE));
    }

    /** One tick of the holder: observe the follows, then settle. */
    private static Optional<AiDecision> settle(TradeTable table, long now) {
        AiTurnContext context = holderAt(table, now);
        TradeCardRules.observe(context);
        return TradeCardRules.settle(context);
    }

    private static Draft draft(TradeTable table) {
        return Draft.read(table.test.memory).orElseThrow();
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // Playing Trade records every other player's strategy tokens, so a token follow can be told from a free one.
    @Test
    void recordPlaySnapshotsStrategyTokens() {
        TradeTable table = holder(true);

        TradeCardRules.recordPlay(holderAt(table, NOW));

        assertThat(table.test.memory.get("tradeTokens|3")).contains("sol:3");
        assertThat(table.test.memory.get("tradeBaseline|3")).contains("");
    }

    // Sol (4 commodities) pays X-2, Letnev (2) X-1, Hacan follows free anyway, and a human close to winning is not
    // offered terms. The terms are stored for the settlements.
    @Test
    void announcesAndStoresTheTerms() {
        TradeTable table = holder(true);
        Player letnev = table.secondAiSeat("letnev", "red");
        letnev.setCommoditiesBase(2);
        table.aiSeat("hacan", "yellow");
        Player xxcha = table.humanSeat("xxcha", "green");
        table.points(xxcha, 8);

        Optional<AiDecision> announcement = TradeCardRules.announce(holderAt(table, NOW));

        assertThat(announcement.orElseThrow()).isInstanceOfSatisfying(AiDecision.Announce.class, announce -> {
            assertThat(announce.text()).contains("**Trade**", "(X−2)", "(X−1)", "close to winning", "even wash");
            assertThat(announce.text().length()).isLessThanOrEqualTo(TradeTerms.MAX_ANNOUNCEMENT);
        });
        assertThat(TradeTerms.decode(table.test.memory.get("tradeTerms|3").orElseThrow()))
                .containsEntry("sol", Optional.of(2))
                .containsEntry("letnev", Optional.of(1))
                .containsEntry("hacan", Optional.empty())
                .containsEntry("xxcha", Optional.empty());
    }

    // The engine lists the holder among the followers; it never bills itself. Sol, who followed free, is billed:
    // its 4 commodities for 2 of Nekro's.
    @Test
    void settlesWithFollowersButNeverTheHolder() {
        TradeTable table = holder(true);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        followed(table, table.nekro);
        assertThat(settle(table, NOW)).isEmpty();

        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        assertThat(pressed(settle(table, NOW))).isEqualTo("transaction");
        Draft draft = draft(table);
        assertThat(draft.partner()).isEqualTo("sol");
        assertThat(draft.purpose()).isEqualTo(Purpose.SETTLEMENT);
        assertThat(draft.target()
                        .sameAs(deal("sendingsol_receivingnekro_Comms_4", "sendingnekro_receivingsol_Comms_2")))
                .isTrue();
    }

    // After a restart the AI no longer knows whom it billed: whoever already followed is left alone, and only
    // players who follow from now on are billed.
    @Test
    void theBaselineSkipsFollowersFromBeforeAMemoryWipe() {
        TradeTable table = holder(true);
        Player letnev = table.secondAiSeat("letnev", "red");
        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        assertThat(settle(table, NOW)).isEmpty();

        letnev.setCommoditiesBase(2);
        table.stock(letnev, 2, 0);
        followed(table, letnev);
        assertThat(pressed(settle(table, NOW))).isEqualTo("transaction");
        assertThat(draft(table).partner()).isEqualTo("letnev");
    }

    // Sol spent a strategy token to follow (3 tokens at the play, 2 now): it owes nothing, so the holder offers an even
    // wash instead of a bill.
    @Test
    void aTokenFollowerGetsAnEvenWash() {
        TradeTable table = holder(true);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.sol.setStrategicCC(2);
        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        settle(table, NOW);

        assertThat(draft(table).purpose()).isEqualTo(Purpose.EVEN_WASH);
        assertThat(draft(table)
                        .target()
                        .sameAs(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3")))
                .isTrue();
    }

    // Following with a token for another card before following Trade for free is not mistaken for a token follow:
    // the snapshot is refreshed every tick for players who have not followed yet.
    @Test
    void aTokenSpentElsewhereBeforeAFreeFollowStillGetsBilled() {
        TradeTable table = holder(true);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.sol.setStrategicCC(2);
        TradeCardRules.observe(holderAt(table, NOW));

        table.stock(table.sol, 4, 0);
        followed(table, table.sol);
        settle(table, NOW + MINUTE);

        assertThat(draft(table).purpose()).isEqualTo(Purpose.SETTLEMENT);
    }

    // Nekro pays the X-k with its commodities first and the rest in trade goods.
    @Test
    void paysWithCommoditiesFirstThenTradeGoods() {
        TradeTable table = holder(true);
        table.stock(table.nekro, 1, 2);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        settle(table, NOW);

        assertThat(draft(table)
                        .target()
                        .sameAs(deal(
                                "sendingsol_receivingnekro_Comms_4",
                                "sendingnekro_receivingsol_Comms_1",
                                "sendingnekro_receivingsol_TGs_1")))
                .isTrue();
    }

    // Nekro has 1 trade good and no commodities left to pay Sol's 2. While another settlement is pending it waits for
    // that one to be accepted (half an hour at most); then it asks for only as many commodities as it can pay for.
    @Test
    void waitsForLiquidityThenAsksForLess() {
        TradeTable table = holder(true);
        Player letnev = table.secondAiSeat("letnev", "red");
        table.stock(table.nekro, 0, 1);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        PendingOffers.record(
                holderAt(table, NOW),
                letnev,
                Purpose.SETTLEMENT,
                new Deal(java.util.List.of(new DealItem("letnev", "nekro", ItemType.COMMODITIES, "2"))));
        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        assertThat(settle(table, NOW)).isEmpty();
        assertThat(settle(table, NOW + 29 * MINUTE)).isEmpty();

        assertThat(pressed(settle(table, NOW + 31 * MINUTE))).isEqualTo("transaction");
        assertThat(draft(table)
                        .target()
                        .sameAs(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_TGs_1")))
                .isTrue();
    }

    // Sol pressed Replenish and Wash (its commodities are already trade goods): it owes k = 2 trade goods, or 2 debt
    // when it has no trade goods either.
    @Test
    void afterReplenishAndWashTheFeeIsKTradeGoodsOrDebt() {
        TradeTable table = holder(true);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.stock(table.sol, 0, 4);
        followed(table, table.sol);
        settle(table, NOW);
        assertThat(draft(table).purpose()).isEqualTo(Purpose.SETTLEMENT_FEE);
        assertThat(draft(table).target().sameAs(deal("sendingsol_receivingnekro_TGs_2")))
                .isTrue();

        TradeTable broke = holder(true);
        TradeCardRules.recordPlay(holderAt(broke, NOW));
        broke.stock(broke.sol, 0, 0);
        followed(broke, broke.sol);
        settle(broke, NOW);
        assertThat(draft(broke).target().sameAs(deal("sendingsol_receivingnekro_SendDebt_2")))
                .isTrue();
    }

    // Not neighbours: no goods can change hands in the action phase, so Sol signs k = 2 debt over to Nekro.
    @Test
    void aNonNeighbourOwesKDebt() {
        TradeTable table = TradeTable.withAiSol();
        table.nekro.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        table.stock(table.nekro, 3, 2);
        table.sol.setCommoditiesBase(4);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        settle(table, NOW);

        assertThat(draft(table).purpose()).isEqualTo(Purpose.SETTLEMENT_FEE);
        assertThat(draft(table).target().sameAs(deal("sendingsol_receivingnekro_SendDebt_2")))
                .isTrue();
        assertThat(draft(table).target().isDebtOnly()).isTrue();
    }

    // A human Nekro no longer trusts (0.4, unpaid debts) is offered no free follow, replenishes anyway and is billed
    // k = 2. Letting that bill expire while it could have paid costs another 0.3 trust.
    @Test
    void billsAnExcludedFreeRiderAndPenalisesNonPayment() {
        TradeTable table = holder(false);
        Trust.adjust(holderAt(table, NOW), table.sol, -0.4);
        TradeCardRules.announce(holderAt(table, NOW));
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        settle(table, NOW);
        Deal bill = draft(table).target();
        assertThat(bill.sameAs(deal("sendingsol_receivingnekro_Comms_4", "sendingnekro_receivingsol_Comms_2")))
                .isTrue();

        // The bill was built and sent as Nekro's offer number 1.
        OfferBuilder.abandon(holderAt(table, NOW));
        bill.items().forEach(item -> table.nekro.addTransactionItem(item.engineString()));
        PendingOffers.record(holderAt(table, NOW), table.sol, Purpose.SETTLEMENT, bill);
        table.game.setStoredValue("offerFromnekroTosol", "1");

        long expired = NOW + Duration.ofHours(25).toMillis();
        AiPrompt rescind = TradeButtons.sent("sent", NOW, table.sol);
        assertThat(pressed(PendingOffers.rescindStale(at(table, table.nekro, expired, rescind))))
                .isEqualTo("rescindOffer_blue");
        assertThat(Trust.of(holderAt(table, expired), table.sol)).isCloseTo(0.1, within(CLOSE));
    }

    // A follower is billed once a round: after the bill is abandoned it is not sent again.
    @Test
    void billsEachFollowerOnce() {
        TradeTable table = holder(true);
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.stock(table.sol, 4, 0);
        followed(table, table.sol);

        assertThat(pressed(settle(table, NOW))).isEqualTo("transaction");
        OfferBuilder.abandon(holderAt(table, NOW));

        assertThat(settle(table, NOW + MINUTE)).isEmpty();
    }

    // Hacan follows Trade without a token anyway (Masters of Trade) and owes nothing: it gets an even wash.
    @Test
    void hacanGetsAnEvenWash() {
        TradeTable table = holder(true);
        Player hacan = table.aiSeat("hacan", "yellow");
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.stock(hacan, 6, 0);
        followed(table, hacan);

        settle(table, NOW);

        assertThat(draft(table).partner()).isEqualTo("hacan");
        assertThat(draft(table).purpose()).isEqualTo(Purpose.EVEN_WASH);
        assertThat(draft(table).target().total("nekro", ItemType.COMMODITIES)).isEqualTo(3);
        assertThat(draft(table).target().total("hacan", ItemType.COMMODITIES)).isEqualTo(3);
    }

    // A human Hacan owes nothing for its free follow: letting the even wash expire costs it no trust.
    @Test
    void hacanIgnoringItsEvenWashKeepsItsTrust() {
        TradeTable table = holder(true);
        Player hacan = table.humanSeat("hacan", "yellow");
        TradeCardRules.recordPlay(holderAt(table, NOW));
        table.stock(hacan, 6, 0);
        followed(table, hacan);
        settle(table, NOW);
        Deal wash = draft(table).target();
        OfferBuilder.abandon(holderAt(table, NOW));
        wash.items().forEach(item -> table.nekro.addTransactionItem(item.engineString()));
        PendingOffers.record(holderAt(table, NOW), hacan, Purpose.EVEN_WASH, wash);
        table.game.setStoredValue("offerFromnekroTohacan", "1");

        long expired = NOW + Duration.ofHours(13).toMillis();
        AiPrompt rescind = TradeButtons.sent("sent", NOW, hacan);
        assertThat(pressed(PendingOffers.rescindStale(at(table, table.nekro, expired, rescind))))
                .isEqualTo("rescindOffer_yellow");
        assertThat(Trust.of(holderAt(table, expired), hacan)).isCloseTo(0.8, within(CLOSE));
    }

    // ---- follower ------------------------------------------------------------------------------------------------

    /** Sol played Trade; Nekro (no commodities of 3, its own Trade Agreement in hand) decides whether to follow. */
    private static TradeTable follower(boolean aiHolder) {
        TradeTable table = aiHolder ? TradeTable.withAiSol() : TradeTable.withHumanSol();
        table.sol.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        table.nekro.setCommoditiesBase(3);
        table.stock(table.nekro, 0, 0);
        table.nekro.setPromissoryNote("black_ta", 1);
        return table;
    }

    private static FollowChoice choice(TradeTable table) {
        return TradeCardRules.followChoice(table.context(), table.sol);
    }

    private static AiPrompt tradeCard() {
        return AiTestGame.prompt("trade", PromptSource.PUBLIC, NOW, "sc_trade_follow", "sc_no_follow_5", "sc_refresh");
    }

    // An AI holder's terms are worth it: as a neighbour Nekro gets 3 - 1 = 2 for commodities it did not have; far
    // away it owes 1 debt for 3 commodities worth 0.3 each (0.9 - 0.8 = 0.1). It replenishes for free.
    @Test
    void followsAnAiHolderFreeWhenTheTermsAreWorthIt() {
        TradeTable far = follower(true);
        assertThat(choice(far)).isEqualTo(FollowChoice.FREE);
        assertThat(pressed(StrategyCardRules.follow(far.test.context(tradeCard()))))
                .isEqualTo("sc_refresh");

        TradeTable near = follower(true);
        near.nekroAndSolNeighbour();
        assertThat(choice(near)).isEqualTo(FollowChoice.FREE);
    }

    // Close to winning, Nekro is offered no terms, so it doesn't take the free replenish.
    @Test
    void declinesWhenExcluded() {
        TradeTable table = follower(true);
        table.points(table.nekro, 8);

        assertThat(choice(table)).isEqualTo(FollowChoice.DECLINE);
    }

    // Someone else holds Nekro's Trade Agreement: replenishing would only feed them.
    @Test
    void declinesWhenItsTradeAgreementIsElsewhere() {
        TradeTable table = follower(true);
        table.nekro.removePromissoryNote("black_ta");
        table.sol.setPromissoryNote("black_ta", 1);

        assertThat(choice(table)).isEqualTo(FollowChoice.DECLINE);
    }

    // Far from Sol it would pay in debt, but it already owes Sol 4, the most it owes anyone.
    @Test
    void declinesWhenTheFeeWouldPassTheDebtCap() {
        TradeTable table = follower(true);
        table.sol.addDebtTokens("black", 4);
        assertThat(choice(table)).isEqualTo(FollowChoice.DECLINE);

        table.nekroAndSolNeighbour();
        assertThat(choice(table)).isEqualTo(FollowChoice.FREE);
    }

    // Hacan follows for free whoever holds Trade, as long as it gains commodities.
    @Test
    void hacanAlwaysFollowsFree() {
        TradeTable table = follower(false);
        Player hacan = table.aiSeat("hacan", "yellow");
        AiTurnContext context = table.contextFor(hacan);

        assertThat(TradeCardRules.followChoice(context, table.sol)).isEqualTo(FollowChoice.FREE);
        table.stock(hacan, hacan.getCommoditiesTotal(), 0);
        assertThat(TradeCardRules.followChoice(context, table.sol)).isEqualTo(FollowChoice.DECLINE);
    }

    // A human holder never offered terms: Nekro spends a strategy token only for 4 or more new commodities it can wash
    // with someone (Hacan here), and only with a token to spare.
    @Test
    void followsAHumanHolderWithATokenOnlyForFourCommoditiesAndAnOutlet() {
        TradeTable table = follower(false);
        table.nekro.setCommoditiesBase(4);
        table.nekro.setStrategicCC(2);
        table.nekro.setTacticalCC(3);
        Player hacan = table.aiSeat("hacan", "yellow");
        assertThat(choice(table)).isEqualTo(FollowChoice.DECLINE);

        table.stock(hacan, 2, 0);
        assertThat(choice(table)).isEqualTo(FollowChoice.TOKEN);
        assertThat(pressed(StrategyCardRules.follow(table.test.context(tradeCard()))))
                .isEqualTo("sc_trade_follow");

        table.nekro.setStrategicCC(0);
        assertThat(StrategyCardRules.follow(table.test.context(tradeCard()))).isEmpty();

        table.nekro.setCommoditiesBase(3);
        assertThat(choice(table)).isEqualTo(FollowChoice.DECLINE);
    }

    // A human holder refreshed Nekro's commodities without asking (Nekro had declined). Nekro honours that holder's
    // first bill in the announced shape, even at a loss, and judges anything after it on value.
    @Test
    void honoursAForcedRefreshOnce() {
        TradeTable table = follower(false);
        table.nekroAndSolNeighbour();
        TradeCardRules.observe(table.context());
        table.stock(table.nekro, 3, 0);
        followed(table, table.nekro);
        TradeCardRules.observe(table.context());
        table.stock(table.sol, 0, 3);
        String[] bill = {"sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_1"};

        assertThat(pressed(OfferResponder.answer(table.test.context(offer(table, "bill", 1, bill)))))
                .isEqualTo("acceptOffer_blue_1");
        assertThat(pressed(OfferResponder.answer(table.test.context(offer(table, "again", 2, bill)))))
                .isNotEqualTo("acceptOffer_blue_2");
    }

    /** Sol's offer number {@code number} to Nekro, as it shows in Nekro's thread. */
    private static AiPrompt offer(TradeTable table, String id, int number, String... items) {
        table.sol.getTransactionItems().clear();
        for (String item : items) table.sol.addTransactionItem(item);
        table.game.setStoredValue("offerFromsolTonekro", String.valueOf(number));
        return TradeButtons.incoming(id, NOW - 1_000L, table.sol, number);
    }

    // The shapes Nekro honours from an AI holder it followed: its commodities for at least 2 fewer back (X-2), or a
    // fee of at most 2 trade goods or 2 debt. A bill outside those shapes is judged on value instead.
    @Test
    void honoursOnlyTheAnnouncedShapes() {
        TradeTable table = follower(true);
        table.nekro.setCommoditiesBase(4);
        table.stock(table.nekro, 4, 3);
        followed(table, table.nekro);
        AiTurnContext context = table.context();

        assertThat(TradeCardRules.honours(
                        context,
                        table.sol,
                        deal("sendingnekro_receivingsol_Comms_4", "sendingsol_receivingnekro_Comms_2")))
                .isTrue();
        assertThat(TradeCardRules.honours(context, table.sol, deal("sendingnekro_receivingsol_TGs_2")))
                .isTrue();
        assertThat(TradeCardRules.honours(context, table.sol, deal("sendingnekro_receivingsol_SendDebt_2")))
                .isTrue();

        assertThat(TradeCardRules.honours(context, table.sol, deal("sendingnekro_receivingsol_TGs_3")))
                .isFalse();
        assertThat(TradeCardRules.honours(
                        context,
                        table.sol,
                        deal("sendingnekro_receivingsol_Comms_4", "sendingsol_receivingnekro_TGs_1")))
                .isFalse();
        assertThat(TradeCardRules.honours(
                        context,
                        table.sol,
                        deal("sendingnekro_receivingsol_Comms_2", "sendingnekro_receivingsol_TGs_1")))
                .isFalse();
    }

    // After following an AI holder for free, Nekro keeps its commodities for that holder's bill for 10 minutes, or
    // until it has honoured it. Only the holder's bill may use them: to anyone else Nekro has none to spare.
    @Test
    void holdsItsCommoditiesForTheHolderForTenMinutes() {
        TradeTable table = follower(true);
        Player hacan = table.aiSeat("hacan", "yellow");
        table.stock(table.nekro, 3, 0);
        TradeCardRules.followPressed(table.context(), table.sol, FollowChoice.FREE);

        AiTurnContext soon = at(table, table.nekro, NOW + 9 * MINUTE);
        assertThat(TradeCardRules.reservedCommodities(soon)).isEqualTo(3);
        assertThat(TradeBudget.freeCommodities(soon)).isZero();
        assertThat(TradeCardRules.holdsFor(soon, table.sol)).isTrue();
        assertThat(TradeBudget.spareCommodities(soon, table.sol)).isEqualTo(3);
        assertThat(TradeBudget.spareCommodities(soon, hacan)).isZero();

        assertThat(TradeCardRules.reservedCommodities(at(table, table.nekro, NOW + 10 * MINUTE)))
                .isZero();

        TradeCardRules.honoured(soon, table.sol);
        assertThat(TradeCardRules.reservedCommodities(soon)).isZero();
        assertThat(TradeBudget.spareCommodities(soon, hacan)).isEqualTo(3);
    }
}
