package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Debt between Nekro (black, the AI) and Sol (blue). Nekro pays its own debt as soon as it can trade with its creditor
 * (and in the agenda phase with the bot's own reminder buttons), collects from human debtors, and never owes one
 * creditor more than 4.
 */
class DebtRulesTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;

    private static TradeTable owing(int debt) {
        TradeTable table = TradeTable.withAiSol();
        table.sol.addDebtTokens(table.nekro.getColor(), debt);
        return table;
    }

    /**
     * The reminder the bot posts to a debtor's game channel at the first agenda: unowned buttons any player could
     * press, so it is told apart by the debtor named at the start of the text.
     */
    private static AiPrompt reminder(Player debtor, Player creditor) {
        String faction = creditor.getFaction();
        AiPrompt buttons = AiTestGame.prompt(
                "debt-" + debtor.getFaction(),
                PromptSource.PUBLIC,
                NOW - 60_000L,
                "sendTGTo_" + faction + "_comm",
                "sendTGTo_" + faction + "_comm3",
                "sendTGTo_" + faction + "_tg",
                "sendTGTo_" + faction + "_tg3",
                "sendTGTo_" + faction + "_debt",
                "sendTGTo_" + faction + "_debt3",
                "deleteButtons");
        return AiTestGame.withContent(
                buttons,
                debtor.getRepresentation() + ", a reminder that you owe debt to " + creditor.getRepresentationNoPing()
                        + " and now could be a good time to pay it (or get it cleared if it was paid already).");
    }

    private static String payInAgenda(TradeTable table, AiPrompt... prompts) {
        table.game.setPhaseOfGame("agendawaiting");
        return DebtRules.payInAgenda(table.test.contextFor(table.nekro, Set.of(), NOW, prompts))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    private static Deal deal(String... raws) {
        return new Deal(Arrays.stream(raws)
                .map(raw -> DealItem.parse(raw, "nekro", "sol").orElseThrow())
                .toList());
    }

    // Commodities before trade goods, and 3 at a time while at least 3 is owed and held.
    @Test
    void paysAgendaDebtWithCommoditiesFirstThreeAtATime() {
        TradeTable table = owing(3);
        AiPrompt reminder = reminder(table.nekro, table.sol);

        table.stock(table.nekro, 3, 5);
        assertThat(payInAgenda(table, reminder)).isEqualTo("sendTGTo_sol_comm3");
        table.stock(table.nekro, 2, 5);
        assertThat(payInAgenda(table, reminder)).isEqualTo("sendTGTo_sol_comm");
        table.stock(table.nekro, 0, 3);
        assertThat(payInAgenda(table, reminder)).isEqualTo("sendTGTo_sol_tg3");
        table.stock(table.nekro, 0, 1);
        assertThat(payInAgenda(table, reminder)).isEqualTo("sendTGTo_sol_tg");
    }

    // "Erase Debt" clears the debt without paying (the bot trusts whoever presses it); the AI never presses it, and
    // never presses a payment it can't cover, since the bot doesn't check.
    @Test
    void neverErasesDebtAndNeverPaysWithoutFunds() {
        TradeTable table = owing(3);
        table.stock(table.nekro, 0, 0);

        assertThat(payInAgenda(table, reminder(table.nekro, table.sol))).isEmpty();
    }

    // The reminder for another debtor has the same buttons; only the one naming Nekro is Nekro's to answer. Paid off,
    // there is nothing to answer either.
    @Test
    void answersOnlyItsOwnReminderWhileItOwes() {
        TradeTable table = owing(3);
        Player letnev = table.secondAiSeat("letnev", "red");
        table.sol.addDebtTokens(letnev.getColor(), 2);
        table.stock(table.nekro, 3, 0);

        assertThat(payInAgenda(table, reminder(letnev, table.sol))).isEmpty();

        TradeTable paid = owing(0);
        paid.stock(paid.nekro, 3, 0);
        assertThat(payInAgenda(paid, reminder(paid.nekro, paid.sol))).isEmpty();
    }

    // Nekro owes its neighbour Sol 4. On its own turn it offers to pay: it asks Sol to clear the debt explicitly, then
    // gives its 2 commodities and 2 of its trade goods; the other 4 trade goods stay with Erect a Monument (8
    // resources, Mordai II pays 4).
    @Test
    void paysItsDebtOnItsOwnTurnKeepingTheScoringReserve() {
        TradeTable table = owing(4);
        table.test.nekroHome();
        table.nekroAndSolNeighbour();
        table.reveal("monument");
        table.stock(table.nekro, 2, 6);

        assertThat(DebtRules.payment(table.nekro, table.sol, 2, 2))
                .hasValueSatisfying(payment -> assertThat(payment.sameAs(deal(
                                "sendingsol_receivingnekro_ClearDebt_4",
                                "sendingnekro_receivingsol_Comms_2",
                                "sendingnekro_receivingsol_TGs_2")))
                        .isTrue());

        AiTurnContext context =
                table.test.contextFor(table.nekro, Set.of(), NOW, TradeButtons.entry("various", NOW - 60_000L));
        assertThat(OwnTurnDeals.start(context).map(AiTestGame::pressedId)).contains("transaction");
        Draft draft = Draft.read(table.test.memory).orElseThrow();
        assertThat(draft.purpose()).isEqualTo(Purpose.DEBT_PAYMENT);
        assertThat(draft.target()
                        .sameAs(deal(
                                "sendingsol_receivingnekro_ClearDebt_4",
                                "sendingnekro_receivingsol_Comms_2",
                                "sendingnekro_receivingsol_TGs_2")))
                .isTrue();
    }

    // Sol is about to win and 2 trade goods would let it score Negotiate Trade Routes: the AI holds its payment back.
    @Test
    void defersPaymentToACreditorAboutToWin() {
        TradeTable table = owing(2);
        table.solHome();
        table.reveal("trade_routes");
        table.stock(table.sol, 0, 3);
        Deal payment = deal("sendingsol_receivingnekro_ClearDebt_2", "sendingnekro_receivingsol_TGs_2");

        assertThat(DebtRules.defers(table.game, table.sol, payment)).isFalse();

        table.points(table.sol, 8);
        assertThat(DebtRules.defers(table.game, table.sol, payment)).isTrue();
        table.stock(table.nekro, 0, 2);
        assertThat(payInAgenda(table, reminder(table.nekro, table.sol))).isEmpty();
    }

    // Nekro holds 3 of a human neighbour's debt: on its turn it asks for Sol's 2 commodities and 1 trade good and
    // clears the 3. Once a round, and never from another AI (AI debtors pay on their own).
    @Test
    void collectsFromHumansOnceARound() {
        TradeTable table = TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.nekro.addDebtTokens(table.sol.getColor(), 3);
        table.stock(table.sol, 2, 4);

        assertThat(OwnTurnDeals.start(turnContext(table)).map(AiTestGame::pressedId))
                .contains("transaction");
        Draft draft = Draft.read(table.test.memory).orElseThrow();
        assertThat(draft.purpose()).isEqualTo(Purpose.DEBT_COLLECTION);
        assertThat(draft.target()
                        .sameAs(deal(
                                "sendingsol_receivingnekro_Comms_2",
                                "sendingsol_receivingnekro_TGs_1",
                                "sendingnekro_receivingsol_ClearDebt_3")))
                .isTrue();

        OfferBuilder.abandon(turnContext(table));
        table.game.setLastActivePlayerChange(new Date(NOW - 500));
        assertThat(OwnTurnDeals.start(turnContext(table))).isEmpty();

        TradeTable ai = TradeTable.withAiSol();
        ai.nekroAndSolNeighbour();
        ai.nekro.addDebtTokens(ai.sol.getColor(), 3);
        ai.stock(ai.sol, 2, 4);
        assertThat(OwnTurnDeals.start(turnContext(ai))).isEmpty();
    }

    // Sol (an AI) pays back the 2 it owes Nekro with 2 commodities and asks Nekro to clear the debt. Debt is already
    // worth 0.9 a token to Nekro, so on value alone the payment would fall short of the margin; being paid in full is
    // accepted anyway. Paying less than is cleared is judged on value.
    @Test
    void acceptsBeingPaidBackInFull() {
        TradeTable table = TradeTable.withAiSol();
        table.nekroAndSolNeighbour();
        table.nekro.addDebtTokens(table.sol.getColor(), 2);
        table.stock(table.sol, 2, 0);
        String[] payment = {"sendingsol_receivingnekro_Comms_2", "sendingnekro_receivingsol_ClearDebt_2"};
        String[] shortPayment = {"sendingsol_receivingnekro_Comms_1", "sendingnekro_receivingsol_ClearDebt_2"};

        assertThat(answer(table, 1, payment)).isEqualTo("acceptOffer_blue_1");
        assertThat(answer(table, 2, shortPayment)).isNotEqualTo("acceptOffer_blue_2");
    }

    private static String answer(TradeTable table, int number, String... items) {
        table.sol.getTransactionItems().clear();
        for (String item : items) table.sol.addTransactionItem(item);
        table.game.setStoredValue("offerFromsolTonekro", String.valueOf(number));
        AiPrompt offer = TradeButtons.incoming("offer-" + number, NOW - 1_000L, table.sol, number);
        return OfferResponder.answer(table.test.contextFor(table.nekro, Set.of(), NOW, offer))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    // Nekro owes Sol 3, so it may sign at most 1 more over to Sol: no package it builds asks for more.
    @Test
    void neverOwesOneCreditorMoreThanFour() {
        TradeTable table = owing(3);
        table.stock(table.nekro, 0, 0);
        table.stock(table.sol, 0, 4);

        assertThat(DebtRules.room(table.nekro, table.sol)).isEqualTo(1);
        List<Deal> packages = OwnTurnDeals.packages(turnContext(table), table.sol, 2, Double.MAX_VALUE);
        assertThat(packages)
                .allSatisfy(candidate ->
                        assertThat(candidate.total("nekro", ItemType.SEND_DEBT)).isLessThanOrEqualTo(1));
        TradeTable full = owing(4);
        assertThat(DebtRules.room(full.nekro, full.sol)).isZero();
    }

    private static AiTurnContext turnContext(TradeTable table) {
        return table.test.contextFor(table.nekro, Set.of(), NOW, TradeButtons.entry("various", NOW - 60_000L));
    }
}
