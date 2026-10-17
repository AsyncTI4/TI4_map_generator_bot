package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.scoring.PaymentRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Sol (blue) sends Nekro (black, the AI) an offer; Nekro accepts, counters or rejects it from the items stored on
 * Sol, never from the message text. Goal 10, round 3, action phase, nobody has scored; Hacan holds commodities and
 * can trade with anyone, so Nekro's commodities always have another outlet (worth 0.6 each to give away).
 */
class OfferResponderTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;
    private static final int TRADE = 5;

    private static TradeTable table(boolean aiSol) {
        TradeTable table = aiSol ? TradeTable.withAiSol() : TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.stock(table.aiSeat("hacan", "red"), 6, 0);
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 3, 0);
        return table;
    }

    /** Sol's offer number {@code number} to Nekro, holding {@code items}, as it shows in Nekro's thread. */
    private static AiPrompt offer(TradeTable table, String id, int number, String... items) {
        return offerFrom(table, table.sol, id, number, items);
    }

    /** {@code offerer}'s offer number {@code number} to Nekro, holding {@code items}. */
    private static AiPrompt offerFrom(TradeTable table, Player offerer, String id, int number, String... items) {
        offerer.getTransactionItems().clear();
        for (String item : items) offerer.addTransactionItem(item);
        table.game.setStoredValue("offerFrom" + offerer.getFaction() + "Tonekro", String.valueOf(number));
        return TradeButtons.incoming(id, NOW - 1_000L, offerer, number);
    }

    private static Optional<AiDecision> answer(TradeTable table, AiPrompt... prompts) {
        return OfferResponder.answer(context(table, prompts));
    }

    private static AiTurnContext context(TradeTable table, AiPrompt... prompts) {
        return table.test.contextFor(table.nekro, Set.of(), NOW, prompts);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private static Deal counterTarget(TradeTable table) {
        return Draft.read(table.test.memory).orElseThrow().target();
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    private static final String[] WASH = {"sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"};

    // C4.4 row 1 against a human: 3.0 - 1.8 = 1.2 for Nekro, minus 0.1 of Sol's 1.5 is 1.05, above the 0.5 margin.
    @Test
    void acceptsAFairWash() {
        TradeTable table = table(false);
        AiPrompt wash = offer(table, "wash", 1, WASH);

        Optional<AiDecision> decision = answer(table, wash);

        assertThat(pressed(decision)).isEqualTo("acceptOffer_blue_1");
        assertThat(decision.orElseThrow())
                .isInstanceOfSatisfying(
                        AiDecision.Press.class,
                        press -> assertThat(press.reason()).startsWith("trade: accept offer from sol"));
        assertThat(answer(table, wash)).isEmpty();
    }

    // Not neighbours in the action phase: commodities can't change hands, so the deal is refused.
    @Test
    void rejectsAnIllegalDeal() {
        TradeTable table = TradeTable.withHumanSol();
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 3, 0);

        assertThat(pressed(answer(table, offer(table, "far", 1, WASH)))).isEqualTo("rejectOffer_blue");
    }

    // Debt is the bot's ledger and may move between any two players: Sol, far away, signs 2 more debt over to Nekro.
    @Test
    void acceptsADebtOnlyDealFromAfar() {
        TradeTable table = TradeTable.withHumanSol();

        assertThat(pressed(answer(table, offer(table, "iou", 1, "sendingsol_receivingnekro_SendDebt_2"))))
                .isEqualTo("acceptOffer_blue_1");
    }

    // The AI can't judge an action card or free-text terms, so it sends the same deal back without them.
    @Test
    void countersWithoutAnActionCardOrFreeTextTerms() {
        TradeTable table = table(false);
        AiPrompt withCard = offer(
                table,
                "with-card",
                1,
                "sendingsol_receivingnekro_Comms_3",
                "sendingnekro_receivingsol_Comms_3",
                "sendingnekro_receivingsol_ACs_generic1");

        assertThat(pressed(answer(table, withCard))).isEqualTo("resetOffer_blue");
        assertThat(counterTarget(table).sameAs(deal(WASH))).isTrue();

        TradeTable second = table(false);
        AiPrompt withTerms = offer(
                second,
                "with-terms",
                1,
                "sendingsol_receivingnekro_Comms_3",
                "sendingnekro_receivingsol_Comms_3",
                "sendingsol_receivingnekro_details_votefin777forfin777sol");
        assertThat(pressed(answer(second, withTerms))).isEqualTo("resetOffer_blue");
        assertThat(counterTarget(second).sameAs(deal(WASH))).isTrue();
    }

    // C4.4, the human example: Sol asks for Nekro's 3 commodities for 2 trade goods. That is worth only 0.1 to Nekro,
    // so it counters with the nearest deal clearing the margin by 0.25: 3 commodities for 3 trade goods (1.2).
    @Test
    void countersAnUnevenAskWithTheNearestFairAmount() {
        TradeTable table = table(false);
        table.stock(table.sol, 0, 4);

        AiPrompt ask = offer(table, "ask", 1, "sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_2");

        assertThat(pressed(answer(table, ask))).isEqualTo("resetOffer_blue");
        assertThat(counterTarget(table)
                        .sameAs(deal("sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_3")))
                .isTrue();
    }

    // At most 2 counter-offers to the same player in one window; the third uneven ask is refused.
    @Test
    void countersTheSamePlayerAtMostTwicePerWindow() {
        TradeTable table = table(false);
        table.stock(table.sol, 0, 4);
        String[] ask = {"sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_2"};

        for (int number = 1; number <= 2; number++) {
            assertThat(pressed(answer(table, offer(table, "ask" + number, number, ask))))
                    .isEqualTo("resetOffer_blue");
            OfferBuilder.abandon(context(table));
        }

        assertThat(pressed(answer(table, offer(table, "ask3", 3, ask)))).isEqualTo("rejectOffer_blue");
    }

    // An Accept whose number is not Sol's latest offer, or whose items are gone (rescinded), would carry out whatever
    // Sol holds now: the AI leaves it alone, and doesn't press Reject on it either.
    @Test
    void leavesStaleOffersAlone() {
        TradeTable table = table(false);
        AiPrompt old = offer(table, "old", 1, WASH);
        table.game.setStoredValue("offerFromsolTonekro", "2");
        assertThat(answer(table, old)).isEmpty();

        TradeTable rescinded = table(false);
        AiPrompt emptied = offer(rescinded, "emptied", 1);
        assertThat(answer(rescinded, emptied)).isEmpty();
    }

    // Sol is rebuilding behind its old Accept button: the items changed since the AI first looked, so the offer is
    // treated as stale.
    @Test
    void leavesAnOfferWhoseItemsChangedAlone() {
        TradeTable table = table(false);
        AiPrompt wash = offer(table, "wash", 1, WASH);
        table.nekro.addTransactionItem("sendingnekro_receivingsol_Comms_1");
        PendingOffers.record(context(table), table.sol, Purpose.WASH, deal("sendingnekro_receivingsol_Comms_1"));
        assertThat(answer(table, wash)).isEmpty();

        table.test.memory.remove("tradePending|sol");
        table.nekro.getTransactionItems().clear();
        table.sol.addTransactionItem("sendingnekro_receivingsol_TGs_1");

        assertThat(answer(table, wash)).isEmpty();
    }

    // One promissory note per side at most.
    @Test
    void rejectsTwoNotes() {
        TradeTable table = table(false);
        table.sol.setPromissoryNote("blue_cf", 11);
        table.sol.setPromissoryNote("blue_ps", 12);

        AiPrompt notes =
                offer(table, "notes", 1, "sendingsol_receivingnekro_PNs_11", "sendingsol_receivingnekro_PNs_12");

        assertThat(pressed(answer(table, notes))).isEqualTo("rejectOffer_blue");
    }

    // Sol offers trade goods it doesn't have.
    @Test
    void rejectsAnOfferTheOffererCannotCover() {
        TradeTable table = table(false);
        table.stock(table.sol, 0, 1);

        AiPrompt broke =
                offer(table, "broke", 1, "sendingsol_receivingnekro_TGs_3", "sendingnekro_receivingsol_Comms_3");

        assertThat(pressed(answer(table, broke))).isEqualTo("rejectOffer_blue");
    }

    // Nekro followed an AI holder's Trade for free, so it pays the announced X-k: its 3 commodities for 1 trade good
    // is a loss on value (1 - 1.8), but it keeps its word. Once per round.
    @Test
    void honoursAnAiTradeHoldersTermsBelowTheMargin() {
        TradeTable table = table(true);
        table.stock(table.sol, 3, 3);
        table.sol.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        table.game.setStoredValue("followedSC" + TRADE + "_3", "_sol_nekro");
        String[] bill = {"sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_1"};

        assertThat(pressed(answer(table, offer(table, "bill", 1, bill)))).isEqualTo("acceptOffer_blue_1");

        assertThat(pressed(answer(table, offer(table, "again", 2, bill)))).isNotEqualTo("acceptOffer_blue_2");
    }

    // Sol holds 3 of Nekro's debt and clears 2 of it for 2 trade goods: Nekro pays. Short of trade goods, it counters
    // with the part it can pay.
    @Test
    void paysAnOwedCollectionOrCountersWithWhatItCanPay() {
        TradeTable table = table(false);
        table.stock(table.nekro, 0, 2);
        table.sol.addDebtTokens("black", 3);
        String[] collect = {"sendingsol_receivingnekro_ClearDebt_2", "sendingnekro_receivingsol_TGs_2"};

        assertThat(pressed(answer(table, offer(table, "collect", 1, collect)))).isEqualTo("acceptOffer_blue_1");

        TradeTable short1 = table(false);
        short1.stock(short1.nekro, 0, 1);
        short1.sol.addDebtTokens("black", 3);
        assertThat(pressed(answer(short1, offer(short1, "collect", 1, collect))))
                .isEqualTo("resetOffer_blue");
        assertThat(counterTarget(short1)
                        .sameAs(deal("sendingsol_receivingnekro_ClearDebt_1", "sendingnekro_receivingsol_TGs_1")))
                .isTrue();
    }

    // C10: the AI owes any one creditor at most 4. Five trade goods for five of Nekro's debt is worth 5 - 4.0 = 1.0 on
    // value, but it would leave Nekro owing Sol 5, and no counter within the cap gives Sol anything (G < 0).
    @Test
    void neverBorrowsPastTheDebtCap() {
        TradeTable table = table(false);
        table.stock(table.sol, 0, 5);
        String[] loan = {"sendingsol_receivingnekro_TGs_5", "sendingnekro_receivingsol_SendDebt_5"};

        assertThat(pressed(answer(table, offer(table, "big-loan", 1, loan)))).isEqualTo("rejectOffer_blue");
        assertThat(OfferBuilder.active(context(table))).isFalse();
    }

    // Three commodities for three of Nekro's debt clears the margin (0.6 - 0.1 x 0.9 = 0.51) and is accepted from a
    // clean slate. When Sol already holds 2 of Nekro's debt it would take Nekro to 5, so Nekro counters with the most
    // it may still owe: 3 commodities for 2 debt.
    @Test
    void countersALoanThatWouldPassTheDebtCap() {
        String[] loan = {"sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_SendDebt_3"};
        TradeTable clean = table(false);
        assertThat(pressed(answer(clean, offer(clean, "loan", 1, loan)))).isEqualTo("acceptOffer_blue_1");

        TradeTable owing = table(false);
        owing.sol.addDebtTokens("black", 2);
        assertThat(pressed(answer(owing, offer(owing, "loan", 1, loan)))).isEqualTo("resetOffer_blue");
        assertThat(counterTarget(owing)
                        .sameAs(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_SendDebt_2")))
                .isTrue();
    }

    // Honouring an AI Trade holder's fee doesn't override the cap either: 2 debt on top of the 3 Nekro already owes
    // the holder would make 5, so the fee is not paid in debt.
    @Test
    void honoursADebtFeeOnlyWithinTheDebtCap() {
        String[] fee = {"sendingnekro_receivingsol_SendDebt_2"};
        TradeTable clean = followedAiTrade();
        assertThat(pressed(answer(clean, offer(clean, "fee", 1, fee)))).isEqualTo("acceptOffer_blue_1");

        TradeTable owing = followedAiTrade();
        owing.sol.addDebtTokens("black", 3);
        assertThat(pressed(answer(owing, offer(owing, "fee", 1, fee)))).isEqualTo("rejectOffer_blue");
    }

    // Nekro replenished for free on Sol's Trade (an AI holder), so its 3 commodities are held for Sol's bill. Until
    // that bill is honoured, Hacan's even wash, which would take them, is refused: without them no counter gives Hacan
    // anything. Paying the wash first would leave Sol's bill uncovered and the free replenish unpaid. Sol's bill
    // itself is honoured from the held commodities, and after it the same wash is welcome.
    @Test
    void keepsTheCommoditiesHeldForTheTradeHoldersBillFromOthers() {
        TradeTable table = followedAiTrade();
        table.nekro.setCommoditiesBase(3);
        Player hacan = table.game.getPlayerFromColorOrFaction("hacan");
        TradeCardRules.followPressed(context(table), table.sol, TradeCardRules.FollowChoice.FREE);
        String[] wash = {"sendinghacan_receivingnekro_Comms_3", "sendingnekro_receivinghacan_Comms_3"};
        String[] bill = {"sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_1"};

        assertThat(pressed(answer(table, offerFrom(table, hacan, "wash", 1, wash))))
                .isEqualTo("rejectOffer_red");
        assertThat(OfferBuilder.active(context(table))).isFalse();

        assertThat(pressed(answer(table, offer(table, "bill", 1, bill)))).isEqualTo("acceptOffer_blue_1");

        assertThat(pressed(answer(table, offerFrom(table, hacan, "wash-again", 2, wash))))
                .isEqualTo("acceptOffer_red_2");
    }

    // A human creditor collecting 2 debt in commodities while Nekro holds them for the Trade holder gets the trade
    // goods Nekro can spare instead.
    @Test
    void paysACollectionDuringTheHoldWithoutTheHeldCommodities() {
        TradeTable table = followedAiTrade();
        table.nekro.setCommoditiesBase(3);
        table.stock(table.nekro, 3, 1);
        Player letnev = table.humanSeat("letnev", "green");
        table.presence(letnev, TradeTable.BESIDE_NEKRO);
        letnev.addDebtTokens("black", 2);
        TradeCardRules.followPressed(context(table), table.sol, TradeCardRules.FollowChoice.FREE);
        String[] collect = {"sendingletnev_receivingnekro_ClearDebt_2", "sendingnekro_receivingletnev_Comms_2"};

        assertThat(pressed(answer(table, offerFrom(table, letnev, "collect", 1, collect))))
                .isEqualTo("resetOffer_green");
        Deal counter = counterTarget(table);
        assertThat(counter.total("nekro", ItemType.COMMODITIES)).isZero();
        assertThat(counter.total("nekro", ItemType.TRADE_GOODS)).isEqualTo(1);
        assertThat(counter.total("letnev", ItemType.CLEAR_DEBT)).isEqualTo(1);
    }

    /** Sol (an AI) played Trade and Nekro followed it for free, so Sol's settlement is honoured on its terms. */
    private static TradeTable followedAiTrade() {
        TradeTable table = table(true);
        table.stock(table.sol, 3, 3);
        table.sol.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        table.game.setStoredValue("followedSC" + TRADE + "_3", "_sol_nekro");
        return table;
    }

    // Lending trade goods against a human's IOU needs trust 0.6; one unpaid Trade fee brought Sol down to 0.5.
    @Test
    void refusesToLendToAHumanItDoesNotTrust() {
        TradeTable table = table(false);
        table.stock(table.nekro, 3, 3);
        Trust.adjust(context(table), table.sol, -0.3);

        AiPrompt loan =
                offer(table, "loan", 1, "sendingnekro_receivingsol_TGs_2", "sendingsol_receivingnekro_SendDebt_3");

        assertThat(pressed(answer(table, loan))).isEqualTo("rejectOffer_blue");
    }

    // Paying for a technology or an objective owns the AI's planets for the moment; offers wait until it is done.
    @Test
    void waitsWhilePaying() {
        TradeTable table = table(false);
        AiPrompt wash = offer(table, "wash", 1, WASH);
        PaymentRules.expectNothing(context(table), "a technology", PaymentRules.TECHNOLOGY_DONE);

        assertThat(answer(table, wash)).isEmpty();
    }

    // With -Dai.trading=false the AI declines every offer, as it did before trading existed.
    @Test
    void rejectsEverythingWithTradingSwitchedOff() {
        TradeTable table = table(false);
        AiPrompt wash = offer(table, "wash", 1, WASH);
        System.setProperty(AiSettings.TRADING_PROPERTY, "false");
        try {
            assertThat(pressed(answer(table, wash))).isEqualTo("rejectOffer_blue");
        } finally {
            System.clearProperty(AiSettings.TRADING_PROPERTY);
        }
    }

    // Sol asks for "a promissory note to be decided" for 3 trade goods. Nekro prices its least harmful note (Political
    // Secret, 1.0), accepts, and remembers which note it priced for when the bot asks it to choose.
    @Test
    void pricesARequestForAnyNoteAndRemembersWhichOne() {
        TradeTable table = table(false);
        table.stock(table.sol, 0, 3);
        table.nekro.addOwnedPromissoryNoteByID("black_ps");
        table.nekro.setPromissoryNote("black_ps", 5);
        Player sol = table.sol;

        AiPrompt note =
                offer(table, "note", 1, "sendingnekro_receivingsol_PNs_generic1", "sendingsol_receivingnekro_TGs_3");

        assertThat(pressed(answer(table, note))).isEqualTo("acceptOffer_blue_1");
        assertThat(NoteGiving.owed(context(table), sol.getFaction())).contains("black_ps");
    }
}
