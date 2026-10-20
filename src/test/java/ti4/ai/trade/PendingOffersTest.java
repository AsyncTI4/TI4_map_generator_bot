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
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * An offer the AI sent stays on record until its items with the partner empty out (accepted), it is superseded, or the
 * AI rescinds it: when the partner's own offer crosses it, it times out, its window closes, or it can no longer be
 * covered. Nekro (black) offers Sol (blue) its 3 commodities for Sol's 3.
 */
class PendingOffersTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;
    private static final long AI_TIMEOUT = Duration.ofMinutes(5).toMillis();
    private static final long HUMAN_SETTLEMENT_TIMEOUT = Duration.ofHours(24).toMillis();
    private static final long SEND_GRACE = Duration.ofSeconds(60).toMillis();
    private static final double CLOSE = 1e-9;
    private static final String[] WASH = {"sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_Comms_3"};

    private static TradeTable table(boolean aiSol) {
        TradeTable table = aiSol ? TradeTable.withAiSol() : TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 3, 0);
        return table;
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    private static AiTurnContext at(TradeTable table, long now, AiPrompt... prompts) {
        return table.test.contextFor(table.nekro, Set.of(), now, prompts);
    }

    /** Nekro's offer to Sol, sent at {@link #NOW} and landed as offer number 1. */
    private static void sent(TradeTable table, Purpose purpose) {
        for (String item : WASH) table.nekro.addTransactionItem(item);
        PendingOffers.record(at(table, NOW), table.sol, purpose, deal(WASH));
        table.game.setStoredValue("offerFromnekroTosol", "1");
    }

    /** Sol's own offer to Nekro, as Nekro's thread shows it. */
    private static AiPrompt solOffers(TradeTable table, long created) {
        table.sol.addTransactionItem("sendingsol_receivingnekro_Comms_2");
        table.game.setStoredValue("offerFromsolTonekro", "1");
        return TradeButtons.incoming("sol-offer", created, table.sol, 1);
    }

    private static String[] theirs(TradeTable table) {
        return table.sol.getTransactionItems().toArray(String[]::new);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // The partner accepted: the AI's items with Sol are gone. A human who accepts earns a little trust.
    @Test
    void anEmptiedOfferWasAccepted() {
        TradeTable table = table(false);
        sent(table, Purpose.WASH);
        table.nekro.clearTransactionItemsWithPlayer(table.sol);

        PendingOffers.observe(at(table, NOW + 10_000L));

        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();
        assertThat(Trust.of(at(table, NOW), table.sol)).isCloseTo(0.9, within(CLOSE));
    }

    // The send press never landed (the bot's offer counter did not move): the record goes after a minute, and the
    // leftover items are then treated as an orphan.
    @Test
    void forgetsASendThatNeverLanded() {
        TradeTable table = table(true);
        for (String item : WASH) table.nekro.addTransactionItem(item);
        PendingOffers.record(at(table, NOW), table.sol, Purpose.WASH, deal(WASH));

        PendingOffers.observe(at(table, NOW + SEND_GRACE / 2));
        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isTrue();

        PendingOffers.observe(at(table, NOW + SEND_GRACE + 1));
        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();
    }

    // A later offer to the same player replaced this one.
    @Test
    void forgetsASupersededOffer() {
        TradeTable table = table(true);
        sent(table, Purpose.WASH);
        table.game.setStoredValue("offerFromnekroTosol", "2");

        PendingOffers.observe(at(table, NOW + 10_000L));

        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();
    }

    // Another AI answers within seconds, so 5 minutes of silence means the offer is dead.
    @Test
    void rescindsAfterFiveMinutesWithAnAi() {
        TradeTable table = table(true);
        sent(table, Purpose.SETTLEMENT);
        AiPrompt mine = TradeButtons.sent("mine", NOW + 1_000L, table.sol);

        assertThat(PendingOffers.rescindStale(at(table, NOW + AI_TIMEOUT - 1, mine)))
                .isEmpty();
        assertThat(pressed(PendingOffers.rescindStale(at(table, NOW + AI_TIMEOUT + 1, mine))))
                .isEqualTo("rescindOffer_blue");
        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();
    }

    // A human gets until the round's action window closes (here: the agenda phase starts), or 24 hours at most for a
    // Trade settlement.
    @Test
    void givesAHumanUntilTheWindowClosesOrADay() {
        TradeTable table = table(false);
        sent(table, Purpose.SETTLEMENT);
        AiPrompt mine = TradeButtons.sent("mine", NOW + 1_000L, table.sol);

        assertThat(PendingOffers.rescindStale(at(table, NOW + AI_TIMEOUT + 1, mine)))
                .isEmpty();
        assertThat(pressed(PendingOffers.rescindStale(at(table, NOW + HUMAN_SETTLEMENT_TIMEOUT + 1, mine))))
                .isEqualTo("rescindOffer_blue");

        TradeTable agenda = table(false);
        sent(agenda, Purpose.SETTLEMENT);
        agenda.game.setPhaseOfGame("agendawaiting");
        assertThat(pressed(PendingOffers.rescindStale(at(agenda, NOW + 60_000L, mine))))
                .isEqualTo("rescindOffer_blue");
    }

    // An unpaid settlement for a free Trade follow costs trust, but only if the follower could have paid.
    @Test
    void penalisesAnUnpaidFreeFollowOnlyWhenItCouldHavePaid() {
        TradeTable covered = table(false);
        sent(covered, Purpose.SETTLEMENT);
        covered.test.memory.put("tradeFollow|3|sol", "free");
        PendingOffers.rescindStale(at(covered, NOW + HUMAN_SETTLEMENT_TIMEOUT + 1));
        assertThat(Trust.of(at(covered, NOW), covered.sol)).isCloseTo(0.5, within(CLOSE));

        TradeTable broke = table(false);
        sent(broke, Purpose.SETTLEMENT);
        broke.test.memory.put("tradeFollow|3|sol", "free");
        broke.stock(broke.sol, 0, 0);
        PendingOffers.rescindStale(at(broke, NOW + HUMAN_SETTLEMENT_TIMEOUT + 1));
        assertThat(Trust.of(at(broke, NOW), broke.sol)).isCloseTo(0.8, within(CLOSE));
    }

    // A human's time to answer ends at the 24 hours or when the offer's window closes, whichever comes first. A bill
    // sent late in the action phase that is still unpaid when the agenda phase starts has run out of time just the
    // same: an hour in, the free follower who could have paid loses 0.3, the debtor who ignored a collection 0.2, and
    // the follower who had nothing left to pay with loses nothing.
    @Test
    void aWindowClosingIsAHumansDeadlineToo() {
        long anHourIn = NOW + Duration.ofHours(1).toMillis();

        TradeTable settlement = table(false);
        sent(settlement, Purpose.SETTLEMENT);
        settlement.test.memory.put("tradeFollow|3|sol", "free");
        settlement.game.setPhaseOfGame("agendawaiting");
        AiPrompt bill = TradeButtons.sent("bill", NOW + 1_000L, settlement.sol);
        assertThat(pressed(PendingOffers.rescindStale(at(settlement, anHourIn, bill))))
                .isEqualTo("rescindOffer_blue");
        assertThat(Trust.of(at(settlement, anHourIn), settlement.sol)).isCloseTo(0.5, within(CLOSE));

        TradeTable collection = table(false);
        sent(collection, Purpose.DEBT_COLLECTION);
        collection.game.setPhaseOfGame("agendawaiting");
        AiPrompt request = TradeButtons.sent("request", NOW + 1_000L, collection.sol);
        assertThat(pressed(PendingOffers.rescindStale(at(collection, anHourIn, request))))
                .isEqualTo("rescindOffer_blue");
        assertThat(Trust.of(at(collection, anHourIn), collection.sol)).isCloseTo(0.6, within(CLOSE));

        TradeTable broke = table(false);
        sent(broke, Purpose.SETTLEMENT);
        broke.test.memory.put("tradeFollow|3|sol", "free");
        broke.stock(broke.sol, 0, 0);
        broke.game.setPhaseOfGame("agendawaiting");
        PendingOffers.rescindStale(at(broke, anHourIn, TradeButtons.sent("bill", NOW + 1_000L, broke.sol)));
        assertThat(PendingOffers.with(at(broke, anHourIn), broke.sol)).isFalse();
        assertThat(Trust.of(at(broke, anHourIn), broke.sol)).isCloseTo(0.8, within(CLOSE));
    }

    // The AI spent the commodities it offered: the offer is withdrawn before anyone accepts a promise it can't keep.
    @Test
    void rescindsAnOfferItCanNoLongerCover() {
        TradeTable table = table(true);
        sent(table, Purpose.WASH);
        AiPrompt mine = TradeButtons.sent("mine", NOW + 1_000L, table.sol);
        assertThat(PendingOffers.rescindStale(at(table, NOW + 10_000L, mine))).isEmpty();

        table.stock(table.nekro, 1, 0);

        assertThat(pressed(PendingOffers.rescindStale(at(table, NOW + 10_000L, mine))))
                .isEqualTo("rescindOffer_blue");
    }

    // Offers crossed: a human's offer always wins, so the AI withdraws its own and answers theirs next.
    @Test
    void givesWayToAHumansCrossingOffer() {
        TradeTable table = table(false);
        sent(table, Purpose.WASH);
        AiPrompt mine = TradeButtons.sent("mine", NOW + 1_000L, table.sol);
        AiPrompt theirs = solOffers(table, NOW - 2_000L);

        assertThat(pressed(PendingOffers.rescindStale(at(table, NOW + 3_000L, mine, theirs))))
                .isEqualTo("rescindOffer_blue");
    }

    // Two AI seats whose offers crossed: the one whose faction sorts first keeps its offer and the other withdraws.
    @Test
    void theAiThatSortsFirstKeepsItsCrossingOffer() {
        TradeTable table = table(true);
        sent(table, Purpose.WASH);
        AiPrompt mine = TradeButtons.sent("mine", NOW + 1_000L, table.sol);
        AiPrompt theirs = solOffers(table, NOW - 2_000L);
        assertThat(PendingOffers.rescindStale(at(table, NOW + 3_000L, mine, theirs)))
                .isEmpty();

        // Sol sorts after Nekro, so from Sol's side the same crossing means withdrawing.
        Player sol = table.sol;
        AiPrompt solCopy = TradeButtons.sent("sol-copy", NOW + 1_000L, table.nekro);
        PendingOffers.record(table.test.contextFor(sol, Set.of(), NOW), table.nekro, Purpose.WASH, deal(theirs(table)));
        AiPrompt nekroOffer = TradeButtons.incoming("nekro-offer", NOW - 2_000L, table.nekro, 1);
        AiTurnContext later = table.test.contextFor(sol, Set.of(), NOW + 3_000L, solCopy, nekroOffer);
        assertThat(pressed(PendingOffers.rescindStale(later))).isEqualTo("rescindOffer_black");
    }

    // An offer from the partner that arrived after ours is its counter-offer (its "Reject and CounterOffer" already
    // deleted ours on its side), so even the AI that sorts first withdraws.
    @Test
    void withdrawsWhenTheAiPartnerCounters() {
        TradeTable table = table(true);
        sent(table, Purpose.WASH);
        AiPrompt mine = TradeButtons.sent("mine", NOW + 1_000L, table.sol);
        AiPrompt counter = solOffers(table, NOW + 30_000L);

        assertThat(pressed(PendingOffers.rescindStale(at(table, NOW + 33_000L, mine, counter))))
                .isEqualTo("rescindOffer_blue");
    }

    // The offer's copy scrolled away: the AI remembered its Rescind button when it was visible and presses that.
    @Test
    void usesTheRememberedRescindButtonAfterScrolling() {
        TradeTable table = table(true);
        sent(table, Purpose.WASH);
        PendingOffers.observe(at(table, NOW + 3_000L, TradeButtons.sent("mine", NOW + 1_000L, table.sol)));

        Optional<AiDecision> decision = PendingOffers.rescindStale(at(table, NOW + AI_TIMEOUT + 1));

        assertThat(pressed(decision)).isEqualTo("rescindOffer_blue");
        assertThat(((AiDecision.Press) decision.orElseThrow()).prompt().messageId())
                .isEqualTo("mine");
    }

    // After an undo or a restart the AI may hold items with no record of sending them. With the offer's Rescind
    // button in view it adopts the offer; without one it clears the items through a fresh transaction, once a round.
    @Test
    void adoptsOrCleansUpOrphanedItems() {
        TradeTable adopting = table(true);
        for (String item : WASH) adopting.nekro.addTransactionItem(item);
        adopting.game.setStoredValue("offerFromnekroTosol", "4");

        PendingOffers.observe(at(adopting, NOW, TradeButtons.sent("mine", NOW - 1_000L, adopting.sol)));

        assertThat(PendingOffers.with(at(adopting, NOW), adopting.sol)).isTrue();
        assertThat(OfferBuilder.active(at(adopting, NOW))).isFalse();

        TradeTable cleaning = table(true);
        for (String item : WASH) cleaning.nekro.addTransactionItem(item);
        PendingOffers.observe(at(cleaning, NOW));
        assertThat(PendingOffers.with(at(cleaning, NOW), cleaning.sol)).isFalse();
        assertThat(OfferBuilder.partner(at(cleaning, NOW))).contains("sol");

        OfferBuilder.abandon(at(cleaning, NOW));
        PendingOffers.observe(at(cleaning, NOW + 60_000L));
        assertThat(OfferBuilder.active(at(cleaning, NOW))).isFalse();
    }

    // An adopted offer gets what was left of the partner's reply time when its copy was posted, not a fresh one: the
    // copy is 4 minutes old, so another AI has 1 minute left to answer.
    @Test
    void anAdoptedOfferExpiresCountingFromItsCopy() {
        TradeTable table = table(true);
        for (String item : WASH) table.nekro.addTransactionItem(item);
        table.game.setStoredValue("offerFromnekroTosol", "4");
        AiPrompt mine = TradeButtons.sent("mine", NOW - Duration.ofMinutes(4).toMillis(), table.sol);

        PendingOffers.observe(at(table, NOW, mine));
        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isTrue();

        long minuteLeft = Duration.ofMinutes(1).toMillis();
        assertThat(PendingOffers.rescindStale(at(table, NOW + minuteLeft - 1_000L, mine)))
                .isEmpty();
        assertThat(pressed(PendingOffers.rescindStale(at(table, NOW + minuteLeft + 1_000L, mine))))
                .isEqualTo("rescindOffer_blue");
    }

    // The bot never deletes the offerer's copy (and its Rescind button) once an offer is accepted, rejected or
    // countered, so an old copy proves nothing about items the AI holds later. Here Sol accepted offer 1, and the AI
    // then started another offer to Sol and gave up half-way: the leftover is cleaned up, not adopted as if sent.
    @Test
    void anAnsweredOffersCopyIsNoProofThatLaterItemsWereSent() {
        TradeTable table = table(true);
        sent(table, Purpose.WASH);
        AiPrompt answered = TradeButtons.sent("answered", NOW + 1_000L, table.sol);
        table.nekro.clearTransactionItemsWithPlayer(table.sol);
        PendingOffers.observe(at(table, NOW + 10_000L, answered));
        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();

        table.nekro.addTransactionItem(WASH[1]);
        PendingOffers.observe(at(table, NOW + 60_000L, answered));

        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();
        assertThat(OfferBuilder.partner(at(table, NOW))).contains("sol");
    }

    // Starting an offer marks every earlier offer to that partner as settled, so a draft abandoned half-built (the
    // round's clean-up already spent, say) is never mistaken for an offer the copy of an older one stands for.
    @Test
    void aHalfBuiltDraftIsNotAdopted() {
        TradeTable table = table(true);
        table.game.setStoredValue("offerFromnekroTosol", "1");
        AiPrompt older = TradeButtons.sent("older", NOW - 60_000L, table.sol);
        OfferBuilder.start(
                at(table, NOW, TradeButtons.entry("various", NOW - 120_000L)), table.sol, deal(WASH), Purpose.WASH);
        table.nekro.addTransactionItem(WASH[1]);
        OfferBuilder.abandon(at(table, NOW));

        PendingOffers.observe(at(table, NOW + 10_000L, older));

        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();
        assertThat(OfferBuilder.partner(at(table, NOW))).contains("sol");
    }

    // After a restart the AI remembers nothing, so it reads the thread: a copy only stands for the items held now if
    // no transaction builder for that partner was posted after it, and if the partner could still be answering it.
    @Test
    void afterARestartOnlyAFreshCopyWithNothingBuiltSinceIsAdopted() {
        TradeTable rebuilt = table(true);
        for (String item : WASH) rebuilt.nekro.addTransactionItem(item);
        rebuilt.game.setStoredValue("offerFromnekroTosol", "1");
        AiPrompt copy = TradeButtons.sent("copy", NOW - 60_000L, rebuilt.sol);
        AiPrompt builder = TradeButtons.builder("builder", NOW - 30_000L, rebuilt.nekro, rebuilt.nekro, rebuilt.sol);
        PendingOffers.observe(at(rebuilt, NOW, copy, builder));
        assertThat(PendingOffers.with(at(rebuilt, NOW), rebuilt.sol)).isFalse();
        assertThat(OfferBuilder.partner(at(rebuilt, NOW))).contains("sol");

        TradeTable old = table(true);
        for (String item : WASH) old.nekro.addTransactionItem(item);
        old.game.setStoredValue("offerFromnekroTosol", "1");
        PendingOffers.observe(at(old, NOW, TradeButtons.sent("old", NOW - AI_TIMEOUT - 1_000L, old.sol)));
        assertThat(PendingOffers.with(at(old, NOW), old.sol)).isFalse();
        assertThat(OfferBuilder.partner(at(old, NOW))).contains("sol");
    }

    // A later offer superseded the one on record (sent by hand, say): its copy is newer than anything the AI closed,
    // so the AI adopts it rather than clearing it.
    @Test
    void adoptsTheOfferThatSupersededItsRecord() {
        TradeTable table = table(true);
        sent(table, Purpose.WASH);
        AiPrompt first = TradeButtons.sent("first", NOW + 1_000L, table.sol);
        AiPrompt second = TradeButtons.sent("second", NOW + 5_000L, table.sol);
        table.game.setStoredValue("offerFromnekroTosol", "2");

        PendingOffers.observe(at(table, NOW + 10_000L, first, second));
        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isFalse();
        PendingOffers.observe(at(table, NOW + 13_000L, first, second));

        assertThat(PendingOffers.with(at(table, NOW), table.sol)).isTrue();
        assertThat(OfferBuilder.active(at(table, NOW))).isFalse();
    }
}
