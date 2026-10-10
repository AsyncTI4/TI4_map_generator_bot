package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * The deals Nekro (black, the AI) starts itself: at the start of its own turn (before its action) and in the agenda
 * phase. In order: paying its own debt, buying trade goods it is short for a spend objective, washing commodities,
 * selling them, and collecting debt from human players. Goal 10, round 3, Nekro is active in the action phase.
 */
class OwnTurnDealsTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;
    private static final int TRADE = 5;

    /** Nekro and its neighbour Sol, each with 3 commodities. */
    private static TradeTable washTable(boolean aiSol) {
        TradeTable table = aiSol ? TradeTable.withAiSol() : TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 3, 0);
        return table;
    }

    /**
     * Nekro controls its home and needs 5 trade goods for Negotiate Trade Routes; it has 3 (and 3 commodities), so it
     * is 2 short. Sol, its neighbour, has 4 trade goods; Hacan (Guild Ships) holds commodities, so Nekro's own
     * commodities have another outlet.
     */
    private static TradeTable twoShort(boolean aiSol) {
        TradeTable table = aiSol ? TradeTable.withAiSol() : TradeTable.withHumanSol();
        table.test.nekroHome();
        table.presence(table.sol, TradeTable.BESIDE_NEKRO);
        table.stock(table.aiSeat("hacan", "red"), 6, 0);
        table.reveal("trade_routes");
        table.stock(table.nekro, 3, 3);
        table.stock(table.sol, 0, 4);
        return table;
    }

    private static AiTurnContext context(TradeTable table, AiPrompt... prompts) {
        return contextFor(table, table.nekro, Set.of(), prompts);
    }

    private static AiTurnContext contextFor(TradeTable table, Player seat, Set<String> pressed, AiPrompt... prompts) {
        AiPrompt[] withEntry = Arrays.copyOf(prompts, prompts.length + 1);
        withEntry[prompts.length] = TradeButtons.entry("various-" + seat.getFaction(), NOW - 60_000L);
        return table.test.contextFor(seat, pressed, NOW, withEntry);
    }

    private static Optional<AiDecision> start(TradeTable table, AiPrompt... prompts) {
        return OwnTurnDeals.start(context(table, prompts));
    }

    private static Draft draft(TradeTable table) {
        return Draft.read(table.test.memory).orElseThrow();
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    /** The AI drops the draft it just started, so the next call shows what it would start instead. */
    private static void abandon(TradeTable table) {
        OfferBuilder.abandon(context(table));
    }

    /** The next turn of the same round. */
    private static void nextTurn(TradeTable table, int turn) {
        table.game.setLastActivePlayerChange(new Date(NOW - 1000 + turn));
    }

    private static Player neighbour(TradeTable table, Player player, int commodities) {
        table.presence(player, TradeTable.BESIDE_NEKRO);
        table.stock(player, commodities, 0);
        return player;
    }

    // Debt comes first, then a purchase it needs for a point, then a wash.
    @Test
    void triesDebtThenDesperationThenAWash() {
        TradeTable owing = twoShort(true);
        owing.stock(owing.sol, 2, 4);
        owing.sol.addDebtTokens(owing.nekro.getColor(), 1);
        assertThat(start(owing).map(AiTestGame::pressedId)).contains("transaction");
        assertThat(draft(owing).purpose()).isEqualTo(Purpose.DEBT_PAYMENT);

        TradeTable shortOnly = twoShort(true);
        shortOnly.stock(shortOnly.sol, 2, 4);
        start(shortOnly);
        assertThat(draft(shortOnly).purpose()).isEqualTo(Purpose.DESPERATION);

        TradeTable washOnly = twoShort(true);
        washOnly.stock(washOnly.sol, 2, 4);
        washOnly.game.getRevealedPublicObjectives().remove("trade_routes");
        start(washOnly);
        assertThat(draft(washOnly).purpose()).isEqualTo(Purpose.WASH);
    }

    // Sol has 2 commodities to wash, Letnev 3: Letnev gives the bigger wash (3 x 0.9 against 2 x 0.9). Two points
    // ahead and the sole leader, Letnev counts for half (3 x 0.5 = 1.5), and Sol is chosen.
    @Test
    void washesWithThePartnerWhoGainsLeast() {
        TradeTable table = washTable(true);
        table.stock(table.sol, 2, 0);
        neighbour(table, table.secondAiSeat("jolnar", "red"), 4);
        start(table);
        assertThat(draft(table).partner()).isEqualTo("jolnar");
        assertThat(draft(table)
                        .target()
                        .sameAs(new Deal(List.of(
                                new DealItem("jolnar", "nekro", ItemType.COMMODITIES, "3"),
                                new DealItem("nekro", "jolnar", ItemType.COMMODITIES, "3")))))
                .isTrue();

        TradeTable ahead = washTable(true);
        ahead.stock(ahead.sol, 2, 0);
        Player leader = neighbour(ahead, ahead.secondAiSeat("jolnar", "red"), 4);
        ahead.points(leader, 2);
        start(ahead);
        assertThat(draft(ahead).partner()).isEqualTo("sol");
    }

    // Jol-Nar would be the better wash, but is skipped while: a deal with it was already made this turn, an offer to it
    // is pending, an offer from it is waiting to be answered, or it is about to win.
    @Test
    void skipsPartnersItCannotOrShouldNotApproach() {
        assertThat(washPartnerWhen((table, jolnar) -> TradeLegality.useWindow(context(table), jolnar, Purpose.WASH)))
                .isEqualTo("sol");
        assertThat(washPartnerWhen((table, jolnar) -> PendingOffers.record(
                        context(table),
                        jolnar,
                        Purpose.WASH,
                        new Deal(List.of(new DealItem("nekro", "jolnar", ItemType.COMMODITIES, "1"))))))
                .isEqualTo("sol");
        assertThat(washPartnerWhen((table, jolnar) -> table.points(jolnar, 8))).isEqualTo("sol");

        TradeTable table = washTable(true);
        table.stock(table.sol, 2, 0);
        Player jolnar = neighbour(table, table.secondAiSeat("jolnar", "red"), 4);
        jolnar.addTransactionItem("sendingjolnar_receivingnekro_Comms_1");
        table.game.setStoredValue("offerFromjolnarTonekro", "1");
        start(table, TradeButtons.incoming("from-jolnar", NOW - 1_000L, jolnar, 1));
        assertThat(draft(table).partner()).isEqualTo("sol");
    }

    private static String washPartnerWhen(java.util.function.BiConsumer<TradeTable, Player> condition) {
        TradeTable table = washTable(true);
        table.stock(table.sol, 2, 0);
        Player jolnar = neighbour(table, table.secondAiSeat("jolnar", "red"), 4);
        condition.accept(table, jolnar);
        start(table);
        return draft(table).partner();
    }

    // Nekro followed Letnev's Trade for free a minute ago, so its commodities are kept for Letnev's bill, and Letnev
    // is not approached for anything else (here: the 2 debt Nekro owes it) until the bill has come or 10 minutes pass.
    @Test
    void leavesTheTradeHolderItFollowedAlone() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        Player letnev = neighbour(table, table.secondAiSeat("letnev", "red"), 0);
        letnev.addSC(TRADE);
        table.game.setSCPlayed(TRADE, true);
        letnev.addDebtTokens(table.nekro.getColor(), 2);
        table.stock(table.nekro, 3, 3);
        TradeCardRules.followPressed(
                table.test.contextFor(table.nekro, Set.of(), NOW - 60_000L), letnev, TradeCardRules.FollowChoice.FREE);

        assertThat(start(table)).isEmpty();

        AiTurnContext later =
                table.test.contextFor(table.nekro, Set.of(), NOW + 10 * 60_000L, TradeButtons.entry("various", NOW));
        assertThat(OwnTurnDeals.start(later).map(AiTestGame::pressedId)).contains("transaction");
        assertThat(draft(table).partner()).isEqualTo("letnev");
        assertThat(draft(table).purpose()).isEqualTo(Purpose.DEBT_PAYMENT);
    }

    // C4.4, rows 4 and 5. Two short of Negotiate Trade Routes, Nekro buys from Sol the cheapest package Sol would
    // accept: its 3 commodities for 2 trade goods (Sol: 3 - 2 = 1.0, minus 0.1 x 6.5 = 0.35). One point ahead, Sol
    // minds Nekro's gain at 0.2 and refuses that (-0.3); the cheapest package it accepts adds 1 debt: 3 + 0.9 - 2 =
    // 1.9 against Nekro's gain 2 - 1.5 - 0.8 + 6 = 5.7, so 1.9 - 1.14 = 0.76.
    @Test
    void buysTheCheapestPackageAnAiWouldAccept() {
        TradeTable peers = twoShort(true);
        start(peers);
        assertThat(draft(peers).purpose()).isEqualTo(Purpose.DESPERATION);
        assertThat(draft(peers)
                        .target()
                        .sameAs(deal("sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_2")))
                .isTrue();

        TradeTable ahead = twoShort(true);
        ahead.points(ahead.nekro, 1);
        ahead.points(ahead.game.getPlayerFromColorOrFaction("hacan"), 1);
        start(ahead);
        assertThat(draft(ahead)
                        .target()
                        .sameAs(deal(
                                "sendingnekro_receivingsol_Comms_3",
                                "sendingnekro_receivingsol_SendDebt_1",
                                "sendingsol_receivingnekro_TGs_2")))
                .isTrue();
    }

    // A human isn't priced by what an AI would accept, but must gain at least 0.5: 2 commodities for 2 trade goods
    // gives Sol nothing, 3 for 2 gives it 1.0. Being a point behind doesn't change that for a human.
    @Test
    void offersAHumanTheCheapestPackageThatGivesThemSomething() {
        TradeTable table = twoShort(false);
        table.points(table.nekro, 1);
        table.points(table.game.getPlayerFromColorOrFaction("hacan"), 1);

        start(table);

        assertThat(draft(table)
                        .target()
                        .sameAs(deal("sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_2")))
                .isTrue();
    }

    // Nobody has commodities to wash: Nekro sells its 3 for 2 of Sol's trade goods. Once Sol has a commodity, a wash
    // is offered instead.
    @Test
    void sellsOnlyWithoutAWashPartner() {
        TradeTable table = washTable(true);
        table.stock(table.sol, 0, 2);
        start(table);
        assertThat(draft(table).purpose()).isEqualTo(Purpose.SELL);
        assertThat(draft(table)
                        .target()
                        .sameAs(deal("sendingnekro_receivingsol_Comms_3", "sendingsol_receivingnekro_TGs_2")))
                .isTrue();

        TradeTable washable = washTable(true);
        washable.stock(washable.sol, 1, 2);
        start(washable);
        assertThat(draft(washable).purpose()).isEqualTo(Purpose.WASH);
    }

    // Two deals a turn: a third partner with commodities waits for the next turn.
    @Test
    void startsAtMostTwoDealsATurn() {
        TradeTable table = washTable(true);
        neighbour(table, table.aiSeat("letnev", "red"), 3);
        neighbour(table, table.secondAiSeat("xxcha", "green"), 3);
        for (int deal = 0; deal < OwnTurnDeals.OWN_TURN_DRAFTS; deal++) {
            assertThat(start(table)).isPresent();
            abandon(table);
        }

        assertThat(start(table)).isEmpty();
        nextTurn(table, 1);
        assertThat(start(table)).isPresent();
    }

    // Two purchases a round: the third turn's best deal is a wash with Hacan instead.
    @Test
    void buysAtMostTwiceARound() {
        TradeTable table = twoShort(true);
        for (int turn = 0; turn < OwnTurnDeals.MAX_DESPERATION_BUYS; turn++) {
            nextTurn(table, turn);
            start(table);
            assertThat(draft(table).purpose()).isEqualTo(Purpose.DESPERATION);
            abandon(table);
        }

        nextTurn(table, OwnTurnDeals.MAX_DESPERATION_BUYS);
        start(table);
        assertThat(draft(table).purpose()).isNotEqualTo(Purpose.DESPERATION);
    }

    // At most 12 new offers an hour, counting settlements and counters.
    @Test
    void startsAtMostTwelveDealsAnHour() {
        TradeTable table = washTable(true);
        String starts = IntStream.range(0, OwnTurnDeals.MAX_TRADE_DRAFTS_PER_HOUR)
                .mapToObj(minute -> String.valueOf(NOW - minute * 60_000L))
                .collect(Collectors.joining(","));
        table.test.memory.put("tradeHour", starts);

        assertThat(start(table)).isEmpty();
    }

    // At most 3 of its own offers waiting for an answer.
    @Test
    void waitsWhileThreeOffersArePending() {
        TradeTable table = washTable(true);
        List<Player> others = List.of(
                table.aiSeat("letnev", "red"), table.secondAiSeat("xxcha", "green"), table.humanSeat("yin", "yellow"));
        for (Player other : others) {
            PendingOffers.record(
                    context(table),
                    other,
                    Purpose.WASH,
                    new Deal(List.of(new DealItem("nekro", other.getFaction(), ItemType.COMMODITIES, "1"))));
        }

        assertThat(start(table)).isEmpty();
    }

    // One unsolicited offer per human player per round.
    @Test
    void approachesAHumanOnceARound() {
        TradeTable table = washTable(false);
        start(table);
        assertThat(draft(table).partner()).isEqualTo("sol");
        abandon(table);

        nextTurn(table, 1);
        assertThat(start(table)).isEmpty();
    }

    // Only at the very start of its turn: not once its action is taken, a strategy card is played, while it pays for
    // something, or on someone else's turn.
    @Test
    void startsNothingAfterItsActionOrWhilePaying() {
        TradeTable acted = washTable(true);
        AiPrompt turn = AiTestGame.prompt("turn", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_tacticalAction");
        Set<String> pressed = Set.of(AiTurnContext.pressKey(turn, turn.buttons().getFirst()));
        assertThat(OwnTurnDeals.start(contextFor(acted, acted.nekro, pressed, turn)))
                .isEmpty();

        TradeTable played = washTable(true);
        AiPrompt card = AiTestGame.prompt("card", PromptSource.PUBLIC, NOW - 500, "FFCC_nekro_strategicAction_1");
        StrategyCardRules.play(context(played, card), card, card.buttons().getFirst(), 1);
        assertThat(start(played)).isEmpty();

        TradeTable paying = washTable(true);
        PaymentRules.expectNothing(context(paying), "a technology", PaymentRules.TECHNOLOGY_DONE);
        assertThat(start(paying)).isEmpty();

        TradeTable notMine = washTable(true);
        notMine.test.isActive(notMine.sol, "action");
        assertThat(start(notMine)).isEmpty();
    }

    // In the agenda phase anyone may trade, but of two AI seats only the one whose faction sorts first starts the
    // wash, so they never cross. A human partner is approached as on its own turn.
    @Test
    void inTheAgendaOnlyTheAiThatSortsFirstStartsAWash() {
        TradeTable table = TradeTable.withAiSol();
        table.stock(table.nekro, 3, 0);
        table.stock(table.sol, 3, 0);
        table.game.setPhaseOfGame("agendawaiting");

        assertThat(OwnTurnDeals.start(contextFor(table, table.sol, Set.of()))).isEmpty();
        assertThat(start(table)).isPresent();
        assertThat(draft(table).purpose()).isEqualTo(Purpose.WASH);

        TradeTable human = TradeTable.withHumanSol();
        human.stock(human.nekro, 3, 0);
        human.stock(human.sol, 3, 0);
        human.game.setPhaseOfGame("agendawaiting");
        assertThat(start(human)).isPresent();
        assertThat(draft(human).partner()).isEqualTo("sol");
    }
}
