package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * The offer builder drives the bot's transaction buttons one press per tick: requests first, then gives, then
 * "Send the Offer". Nekro (black) is the AI building the offer and Sol (blue) its neighbour. Every step's message is
 * posted a second after the press that caused it, and a tick happens every 3 seconds.
 */
class OfferBuilderTest extends BaseTi4Test {

    private static final long TICK = 3_000L;
    private static final long POSTED_AFTER_PRESS = 1_000L;

    private TradeTable table;
    private Player nekro;
    private Player sol;
    private long now;
    private int messages;
    private final Set<String> pressedKeys = new HashSet<>();
    private final List<String> pressedIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        nekro = table.nekro;
        sol = table.sol;
        table.stock(nekro, 3, 0);
        table.stock(sol, 4, 0);
        now = AiTestGame.NOW;
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return table.test.contextFor(nekro, Set.copyOf(pressedKeys), now, prompts);
    }

    private Optional<AiDecision> start(Deal target, Purpose purpose) {
        Optional<AiDecision> decision =
                OfferBuilder.start(context(TradeButtons.entry("various", now - 60_000L)), sol, target, purpose);
        remember(decision);
        return decision;
    }

    /** One tick later, with these messages on screen. */
    private Optional<AiDecision> tick(AiPrompt... prompts) {
        now += TICK;
        Optional<AiDecision> decision = OfferBuilder.continueDraft(context(prompts));
        remember(decision);
        return decision;
    }

    private void remember(Optional<AiDecision> decision) {
        if (decision.orElse(null) instanceof AiDecision.Press press) {
            pressedKeys.add(AiTurnContext.pressKey(press.prompt(), press.button()));
            pressedIds.add(press.button().customId());
        }
    }

    private long posted() {
        return now + POSTED_AFTER_PRESS;
    }

    private String nextId() {
        return "message-" + (++messages);
    }

    private AiPrompt playerPicker() {
        return TradeButtons.playerPicker(nextId(), posted(), nekro, sol);
    }

    private AiPrompt offering() {
        return TradeButtons.builder(nextId(), posted(), nekro, nekro, sol);
    }

    private AiPrompt asking() {
        return TradeButtons.builder(nextId(), posted(), nekro, sol, nekro);
    }

    private AiPrompt picker(String type, Player sender, Player receiver, String... details) {
        return TradeButtons.picker(nextId(), posted(), type, sender, receiver, details);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private static String pressedMessage(Optional<AiDecision> decision) {
        return decision.map(made -> ((AiDecision.Press) made).prompt().messageId())
                .orElse("");
    }

    // The worked example of the spec: Sol followed Trade with 4 commodities and owes k = 2, so the holder asks for
    // Sol's 4 commodities and pays 2 of its own. Asking comes first, then giving, then sending: nine presses.
    @Test
    void buildsTheTradeSettlementInNinePresses() {
        Deal target = deal("sendingsol_receivingnekro_Comms_4", "sendingnekro_receivingsol_Comms_2");

        assertThat(pressed(start(target, Purpose.SETTLEMENT))).isEqualTo("transaction");
        assertThat(pressed(tick(playerPicker()))).isEqualTo("FFCC_nekro_transactWith_sol");
        assertThat(pressed(tick(offering()))).isEqualTo("getNewTransaction_blue_black");
        assertThat(pressed(tick(asking()))).isEqualTo("newTransact_Comms_blue_black");
        assertThat(pressed(tick(picker("Comms", sol, nekro, TradeButtons.upTo(4)))))
                .isEqualTo("offerToTransact_Comms_blue_black_4");
        nekro.addTransactionItem("sendingsol_receivingnekro_Comms_4");
        assertThat(pressed(tick(asking()))).isEqualTo("getNewTransaction_black_blue");
        assertThat(pressed(tick(offering()))).isEqualTo("newTransact_Comms_black_blue");
        assertThat(pressed(tick(picker("Comms", nekro, sol, TradeButtons.upTo(3)))))
                .isEqualTo("offerToTransact_Comms_black_blue_2");
        nekro.addTransactionItem("sendingnekro_receivingsol_Comms_2");
        assertThat(pressed(tick(offering()))).isEqualTo("sendOffer_blue");

        assertThat(pressedIds).hasSize(9);
        assertThat(OfferBuilder.active(context())).isFalse();
        assertThat(PendingOffers.with(context(), sol)).isTrue();
        // The builder offers a form, a one-click wash and a play-area return; none of them is ever pressed.
        assertThat(pressedIds)
                .noneMatch(id -> id.contains("~MDL")
                        || id.contains("washComms")
                        || id.contains("startReturnPNInPlayArea_")
                        || id.contains("Details"));
    }

    // A stale Accept pressed mid-build then only makes the partner pay: the AI asks for Sol's commodities before it
    // puts its own trade goods in.
    @Test
    void asksBeforeItGives() {
        table.stock(nekro, 0, 5);
        start(deal("sendingnekro_receivingsol_TGs_2", "sendingsol_receivingnekro_Comms_3"), Purpose.COUNTER);
        tick(playerPicker());

        assertThat(pressed(tick(offering()))).isEqualTo("getNewTransaction_blue_black");
        assertThat(pressed(tick(asking()))).isEqualTo("newTransact_Comms_blue_black");
    }

    // The trade goods picker stops at 20 and the debt picker at 5; the builder presses the largest amount that fits
    // and comes back for the rest.
    @Test
    void repeatsAmountsPastThePickerCaps() {
        table.stock(sol, 4, 25);
        start(deal("sendingsol_receivingnekro_TGs_23", "sendingnekro_receivingsol_SendDebt_7"), Purpose.SETTLEMENT);
        tick(playerPicker());
        tick(offering());
        tick(asking());

        assertThat(pressed(tick(picker("TGs", sol, nekro, TradeButtons.upTo(20)))))
                .isEqualTo("offerToTransact_TGs_blue_black_20");
        nekro.addTransactionItem("sendingsol_receivingnekro_TGs_20");
        assertThat(pressed(tick(asking()))).isEqualTo("newTransact_TGs_blue_black");
        assertThat(pressed(tick(picker("TGs", sol, nekro, TradeButtons.upTo(20)))))
                .isEqualTo("offerToTransact_TGs_blue_black_3");
        nekro.addTransactionItem("sendingsol_receivingnekro_TGs_3");

        assertThat(pressed(tick(asking()))).isEqualTo("getNewTransaction_black_blue");
        assertThat(pressed(tick(offering()))).isEqualTo("newTransact_SendDebt_black_blue");
        assertThat(pressed(tick(picker("SendDebt", nekro, sol, TradeButtons.upTo(5)))))
                .isEqualTo("offerToTransact_SendDebt_black_blue_5");
        nekro.addTransactionItem("sendingnekro_receivingsol_SendDebt_5");
        assertThat(pressed(tick(offering()))).isEqualTo("newTransact_SendDebt_black_blue");
        assertThat(pressed(tick(picker("SendDebt", nekro, sol, TradeButtons.upTo(5)))))
                .isEqualTo("offerToTransact_SendDebt_black_blue_2");
    }

    // A promissory note is picked by its number in the AI's hand, never by its name.
    @Test
    void picksTheNoteByItsHandNumber() {
        nekro.setPromissoryNote("black_cf", 8);
        nekro.setPromissoryNote("black_ps", 7);
        start(deal("sendingnekro_receivingsol_PNs_7", "sendingsol_receivingnekro_Comms_1"), Purpose.SETTLEMENT);
        tick(playerPicker());
        nekro.addTransactionItem("sendingsol_receivingnekro_Comms_1");

        assertThat(pressed(tick(offering()))).isEqualTo("newTransact_PNs_black_blue");
        assertThat(pressed(tick(picker("PNs", nekro, sol, "8", "7")))).isEqualTo("offerToTransact_PNs_black_blue_7");
    }

    // Items the target does not hold mean the builder went wrong: start over from the builder's own Reset. An old
    // offer from Sol sits in the thread with the same Reset id; it is not the one pressed.
    @Test
    void resetsCorruptItemsFromTheBuilderNotFromAnIncomingOffer() {
        start(deal("sendingsol_receivingnekro_Comms_4", "sendingnekro_receivingsol_Comms_2"), Purpose.SETTLEMENT);
        tick(playerPicker());
        nekro.addTransactionItem("sendingnekro_receivingsol_TGs_1");
        AiPrompt oldOffer = TradeButtons.incoming("old-offer", posted(), sol, 1);
        AiPrompt builder = offering();

        Optional<AiDecision> decision = tick(oldOffer, builder);

        assertThat(pressed(decision)).isEqualTo("resetOffer_blue");
        assertThat(pressedMessage(decision)).isEqualTo(builder.messageId());
        assertThat(OfferBuilder.active(context())).isTrue();
    }

    // Sol has no trade goods to give, so the builder has no row for them: the draft is abandoned. With items already
    // in, the builder's Reset clears them, and the fresh builder it posts is deleted.
    @Test
    void abandonsWhenARowIsMissing() {
        start(deal("sendingsol_receivingnekro_Comms_3", "sendingsol_receivingnekro_TGs_2"), Purpose.SETTLEMENT);
        tick(playerPicker());
        nekro.addTransactionItem("sendingsol_receivingnekro_Comms_3");
        AiPrompt builder = asking();

        Optional<AiDecision> reset = tick(builder);

        assertThat(pressed(reset)).isEqualTo("resetOffer_blue");
        assertThat(pressedMessage(reset)).isEqualTo(builder.messageId());
        nekro.clearTransactionItemsWithPlayer(sol);
        assertThat(pressed(tick(builder, offering()))).isEqualTo("deleteButtons");
        assertThat(OfferBuilder.active(context())).isFalse();
    }

    // With items in and no builder on screen to reset, the AI cleans up through a fresh transaction instead:
    // Transaction, Sol, Delete. Only once per partner per round.
    @Test
    void cleansUpThroughAFreshTransactionWhenNoBuilderIsVisible() {
        start(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"), Purpose.SETTLEMENT);
        nekro.addTransactionItem("sendingsol_receivingnekro_Comms_3");
        now += OfferBuilder.DRAFT_MAX_AGE_MILLIS;

        assertThat(pressed(tick(TradeButtons.entry("various-again", posted() - 60_000L))))
                .isEqualTo("transaction");
        assertThat(pressed(tick(playerPicker()))).isEqualTo("FFCC_nekro_transactWith_sol");
        nekro.clearTransactionItemsWithPlayer(sol);
        assertThat(pressed(tick(offering()))).isEqualTo("deleteButtons");
        assertThat(OfferBuilder.active(context())).isFalse();

        start(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"), Purpose.SETTLEMENT);
        nekro.addTransactionItem("sendingsol_receivingnekro_Comms_3");
        now += OfferBuilder.DRAFT_MAX_AGE_MILLIS;
        assertThat(tick()).isEmpty();
        assertThat(OfferBuilder.active(context())).isFalse();
    }

    // Nothing appears after a press: wait up to 20 seconds, try once more from the Transaction button, then give up.
    @Test
    void waitsThenRestartsOnceThenGivesUp() {
        start(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"), Purpose.SETTLEMENT);
        AiPrompt various = TradeButtons.entry("various", AiTestGame.NOW - 60_000L);

        assertThat(tick(various)).containsInstanceOf(AiDecision.Wait.class);
        now = AiTestGame.NOW + OfferBuilder.STEP_WAIT_MILLIS;
        assertThat(pressed(tick(various))).isEqualTo("transaction");
        now += OfferBuilder.STEP_WAIT_MILLIS;

        assertThat(tick(various)).isEmpty();
        assertThat(OfferBuilder.active(context())).isFalse();
    }

    // A draft never lives longer than 10 minutes.
    @Test
    void abandonsADraftOlderThanTenMinutes() {
        start(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"), Purpose.SETTLEMENT);
        now += OfferBuilder.DRAFT_MAX_AGE_MILLIS;

        assertThat(tick(playerPicker())).isEmpty();
        assertThat(OfferBuilder.active(context())).isFalse();
    }

    // Sol sent an offer of its own meanwhile: the AI drops its half-built offer and answers Sol's instead.
    @Test
    void abandonsWhenThePartnerSendsAnOffer() {
        start(deal("sendingsol_receivingnekro_Comms_3", "sendingnekro_receivingsol_Comms_3"), Purpose.SETTLEMENT);
        tick(playerPicker());
        nekro.addTransactionItem("sendingsol_receivingnekro_Comms_3");
        sol.addTransactionItem("sendingsol_receivingnekro_Comms_2");
        table.game.setStoredValue("offerFromsolTonekro", "1");
        AiPrompt theirs = TradeButtons.incoming("their-offer", posted(), sol, 1);
        AiPrompt builder = offering();

        Optional<AiDecision> decision = tick(theirs, builder);

        assertThat(pressed(decision)).isEqualTo("resetOffer_blue");
        assertThat(pressedMessage(decision)).isEqualTo(builder.messageId());
    }

    // Coverage is checked again at the last moment: the AI spent its commodities while building, so it abandons the
    // offer instead of sending a promise it cannot keep.
    @Test
    void checksCoverageAgainBeforeSending() {
        start(deal("sendingsol_receivingnekro_Comms_4", "sendingnekro_receivingsol_Comms_2"), Purpose.SETTLEMENT);
        tick(playerPicker());
        nekro.addTransactionItem("sendingsol_receivingnekro_Comms_4");
        nekro.addTransactionItem("sendingnekro_receivingsol_Comms_2");
        table.stock(nekro, 1, 0);

        assertThat(pressed(tick(offering()))).isEqualTo("resetOffer_blue");
        assertThat(PendingOffers.with(context(), sol)).isFalse();
    }
}
