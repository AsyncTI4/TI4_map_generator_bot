package ti4.ai.expedition;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// An expedition is done at the end of a turn: the end-of-turn abilities menu, "Do an Expedition", the slice to claim,
// then paying for it. The AI walks the menus, pays exactly what it planned and never leaves a payment half-done.
class ExpeditionRulesTest extends BaseTi4Test {

    private static final String DONE = "Done Exhausting Planets";

    private AiTestGame test;
    private Player mahact;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        mahact = test.addSeat("200000000000000009", "mahact", "green");
        mahact.addBreakthrough("mahactbt");
        test.isActive(mahact, "action");
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(mahact, Set.of(), NOW + 10, prompts);
    }

    private AiTurnContext contextWithPressed(Set<String> pressed, AiPrompt... prompts) {
        return test.contextFor(mahact, pressed, NOW + 10, prompts);
    }

    private AiPrompt endTurn() {
        return prompt("end", PromptSource.PUBLIC, NOW, "FFCC_mahact_endOfTurnAbilities", "FFCC_mahact_componentAction");
    }

    private AiPrompt abilities() {
        return prompt(
                "abilities",
                PromptSource.PUBLIC,
                NOW + 1,
                "FFCC_mahact_turnEnd",
                "FFCC_mahact_expeditionInfoAndButtons");
    }

    private AiPrompt choices() {
        return prompt(
                "choices",
                PromptSource.PUBLIC,
                NOW + 2,
                "FFCC_mahact_TEexpedition_techSkip",
                "FFCC_mahact_TEexpedition_tradeGoods",
                "FFCC_mahact_TEexpedition_fiveRes",
                "FFCC_mahact_TEexpedition_fiveInf",
                "FFCC_mahact_TEexpedition_secret",
                "FFCC_mahact_TEexpedition_actionCards",
                "deleteButtons");
    }

    private AiPrompt payment() {
        return prompt(
                "pay",
                PromptSource.PUBLIC,
                NOW + 3,
                List.of("spend_tarmann_skips", "resetSpend_skips", "deleteButtons_spitItOut"),
                List.of("Tarmann", "Reset", DONE));
    }

    private void tarmann() {
        test.place("22", "202");
        mahact.addPlanet("tarmann");
    }

    private static String id(Optional<AiDecision> decision) {
        return pressedId(decision.orElseThrow());
    }

    // Opens the end-of-turn abilities, then "Do an Expedition", then claims the slice a technology specialty planet
    // pays for, exhausts exactly that planet and closes the payment.
    @Test
    void walksTheMenusAndPaysWithTheTechnologySpecialtyPlanet() {
        tarmann();
        AiPrompt end = endTurn();
        assertThat(id(ExpeditionRules.endOfTurn(context(end), List.of(end))))
                .isEqualTo("FFCC_mahact_endOfTurnAbilities");

        AiPrompt menu = abilities();
        assertThat(id(ExpeditionRules.endOfTurn(context(menu), List.of(menu))))
                .isEqualTo("FFCC_mahact_expeditionInfoAndButtons");

        AiPrompt slices = choices();
        assertThat(id(ExpeditionRules.next(context(slices)))).isEqualTo("FFCC_mahact_TEexpedition_techSkip");
        assertThat(PaymentRules.isPending(context())).isTrue();

        AiPrompt pay = payment();
        assertThat(id(PaymentRules.pay(context(pay)))).isEqualTo("spend_tarmann_skips");
        mahact.exhaustPlanet("tarmann");
        assertThat(id(PaymentRules.pay(context(pay)))).isEqualTo("deleteButtons_spitItOut");
        assertThat(PaymentRules.isPending(context())).isFalse();
        assertThat(ExpeditionRules.next(context(pay))).isEmpty();
    }

    // The rules never leave while a payment is outstanding: they hold the turn until the payment prompt shows.
    @Test
    void waitsForThePaymentPromptInsteadOfEndingTheTurn() {
        tarmann();
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));
        ExpeditionRules.next(context(choices()));

        assertThat(ExpeditionRules.next(context())).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void givesUpWaitingForThePaymentAfterAMinute() {
        tarmann();
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));
        ExpeditionRules.next(context(choices()));

        AiTurnContext later =
                test.contextFor(mahact, Set.of(), NOW + 10 + ExpeditionRules.STEP_TIMEOUT_MILLIS + 1, new AiPrompt[0]);
        assertThat(ExpeditionRules.next(later)).isEmpty();
        assertThat(PaymentRules.isPending(later)).isFalse();
    }

    // Three trade goods are taken automatically: nothing to pay, and the expedition is done in one claim.
    @Test
    void tradeGoodsAreDeductedWithoutAPaymentPrompt() {
        mahact.setTg(3);
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));

        assertThat(id(ExpeditionRules.next(context(choices())))).isEqualTo("FFCC_mahact_TEexpedition_tradeGoods");
        assertThat(PaymentRules.isPending(context())).isFalse();
        assertThat(ExpeditionRules.next(context())).isEmpty();
    }

    @Test
    void doesOneExpeditionPerTurn() {
        mahact.setTg(3);
        AiPrompt menu = abilities();
        assertThat(ExpeditionRules.endOfTurn(context(menu), List.of(menu))).isPresent();

        assertThat(ExpeditionRules.endOfTurn(context(menu), List.of(menu))).isEmpty();
    }

    @Test
    void doesNothingWhenNoExpeditionIsWorthIt() {
        AiPrompt menu = abilities();
        AiPrompt end = endTurn();

        assertThat(ExpeditionRules.endOfTurn(context(menu), List.of(menu))).isEmpty();
        assertThat(ExpeditionRules.endOfTurn(context(end), List.of(end))).isEmpty();
    }

    @Test
    void doesNotOpenAMenuThatHasNoExpeditionToOffer() {
        mahact.setTg(3);
        AiPrompt start = prompt("start", PromptSource.PUBLIC, NOW, "FFCC_mahact_tacticalAction");

        assertThat(ExpeditionRules.endOfTurn(context(start), List.of(start))).isEmpty();
    }

    // A rival that took the slice while the AI was clicking must not be overwritten by a stale button.
    @Test
    void dropsTheClaimWhenARivalTookTheSliceInTheMeantime() {
        mahact.setTg(3);
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));
        test.game.getExpeditions().setTradeGoods("sol");

        assertThat(ExpeditionRules.next(context(choices()))).isEmpty();
    }

    @Test
    void waitsForTheSlicesToAppearThenGivesUp() {
        mahact.setTg(3);
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));

        assertThat(ExpeditionRules.next(context())).containsInstanceOf(AiDecision.Wait.class);
        AiTurnContext later =
                test.contextFor(mahact, Set.of(), NOW + 10 + ExpeditionRules.STEP_TIMEOUT_MILLIS + 1, new AiPrompt[0]);
        assertThat(ExpeditionRules.next(later)).isEmpty();
    }

    // Passing is a turn too, and its menu carries the expedition button.
    @Test
    void doesTheExpeditionFromThePassingMenu() {
        mahact.setTg(3);
        AiPrompt pass = prompt("pass", PromptSource.PUBLIC, NOW, "FFCC_mahact_passingAbilities");
        assertThat(id(ExpeditionRules.beforePassing(context(pass), List.of(pass))))
                .isEqualTo("FFCC_mahact_passingAbilities");

        AiPrompt menu = prompt(
                "menu",
                PromptSource.PUBLIC,
                NOW + 1,
                "FFCC_mahact_passForRound",
                "FFCC_mahact_expeditionInfoAndButtons");
        assertThat(id(ExpeditionRules.beforePassing(context(menu), List.of(menu))))
                .isEqualTo("FFCC_mahact_expeditionInfoAndButtons");
    }

    // The secret objective buttons arrive in the private thread (the bot's ephemeral message is re-posted there).
    @Test
    void discardsTheLeastPromisingSecretFromThePrivateThread() {
        mahact.setSecret("dts", 411);
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));
        assertThat(id(ExpeditionRules.next(context(choices())))).isEqualTo("FFCC_mahact_TEexpedition_secret");

        AiPrompt discard = prompt("discard", PromptSource.AI_THREAD, NOW + 3, "discardSecret_411");
        assertThat(id(ExpeditionRules.next(context(discard)))).isEqualTo("discardSecret_411");

        mahact.removeSecret(411);
        assertThat(ExpeditionRules.next(context())).isEmpty();
    }

    // The discard message disappears after the first card, so the second one is asked for from the hand message.
    @Test
    void discardsTwoActionCardsOneAtATime() {
        mahact.setActionCard("mining_initiative", 412);
        mahact.setActionCard("bribery", 413);
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));
        assertThat(id(ExpeditionRules.next(context(choices())))).isEqualTo("FFCC_mahact_TEexpedition_actionCards");

        AiPrompt first = prompt(
                "first", PromptSource.AI_THREAD, NOW + 3, "ac_discard_from_hand_412", "ac_discard_from_hand_413");
        String firstDiscard = id(ExpeditionRules.next(context(first)));
        assertThat(firstDiscard).startsWith("ac_discard_from_hand_41");

        mahact.removeActionCard(firstDiscard.endsWith("412") ? 412 : 413);
        AiPrompt hand = prompt("hand", PromptSource.AI_THREAD, NOW + 4, "getDiscardButtonsACs");
        assertThat(id(ExpeditionRules.next(context(hand)))).isEqualTo("getDiscardButtonsACs");

        String left = firstDiscard.endsWith("412") ? "413" : "412";
        AiPrompt second = prompt("second", PromptSource.AI_THREAD, NOW + 5, "ac_discard_from_hand_" + left);
        assertThat(id(ExpeditionRules.next(context(second)))).isEqualTo("ac_discard_from_hand_" + left);
    }

    @Test
    void doesNotPressAButtonItAlreadyPressed() {
        mahact.setTg(3);
        AiPrompt menu = abilities();
        ExpeditionRules.endOfTurn(context(menu), List.of(menu));
        AiPrompt slices = choices();
        Set<String> pressed =
                Set.of(AiTurnContext.pressKey(slices, slices.buttons().get(1)));

        assertThat(ExpeditionRules.next(contextWithPressed(pressed, slices))).containsInstanceOf(AiDecision.Wait.class);
    }
}
