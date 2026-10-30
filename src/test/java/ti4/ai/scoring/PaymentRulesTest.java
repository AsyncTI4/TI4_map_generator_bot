package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

class PaymentRulesTest extends BaseTi4Test {

    private static final String DONE = "Done Exhausting Planets";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("statusScoring");
        test.nekro.addPlanet("mordaiii");
        test.nekro.setTg(3);
    }

    private AiPrompt nekroPayment() {
        return prompt(
                "pay",
                PromptSource.PUBLIC,
                NOW,
                List.of("spend_mordaiii_both", "reduceTG_1_both", "deleteButtons"),
                List.of("Mordai II", "Spend 1 Trade Good", DONE));
    }

    // The bot trusts the player to pay for objectives it scored, so the AI exhausts exactly what it planned:
    // the planets first, then trade goods down to the planned amount, then it closes the prompt.
    @Test
    void paysThePlannedPlanetsAndTradeGoodsThenFinishes() {
        PaymentRules.expect(
                test.context(),
                "an objective",
                new Wallet.Payment(List.of("mordaiii"), List.of(), 1, 0),
                PaymentRules.OBJECTIVE_DONE);

        assertThat(pressed(nekroPayment())).contains("spend_mordaiii_both");

        test.nekro.exhaustPlanet("mordaiii");
        assertThat(pressed(nekroPayment())).contains("reduceTG_1_both");

        test.nekro.setTg(2);
        assertThat(pressed(nekroPayment())).contains("deleteButtons");
        assertThat(PaymentRules.isPending(test.context())).isFalse();
    }

    // Spend buttons act for whoever presses them, so another seat's payment prompt must never be touched.
    @Test
    void ignoresAnotherSeatsPaymentPrompt() {
        PaymentRules.expect(
                test.context(),
                "an objective",
                new Wallet.Payment(List.of("mordaiii"), List.of(), 0, 0),
                PaymentRules.OBJECTIVE_DONE);
        test.sol.addPlanet("jord");
        AiPrompt solPayment = prompt(
                "sol-pay",
                PromptSource.PUBLIC,
                NOW,
                List.of("spend_jord_both", "deleteButtons"),
                List.of("Jord", DONE));

        assertThat(PaymentRules.pay(test.context(solPayment))).isEmpty();
    }

    // A payment belongs to the phase it was made in: once the status phase moves on, old prompts are left alone.
    @Test
    void forgetsThePaymentWhenThePhaseChanges() {
        PaymentRules.expect(
                test.context(),
                "an objective",
                new Wallet.Payment(List.of("mordaiii"), List.of(), 0, 0),
                PaymentRules.OBJECTIVE_DONE);
        test.game.setPhaseOfGame("statusHomework");

        assertThat(PaymentRules.pay(test.context(nekroPayment()))).isEmpty();
    }

    // A pending objective payment waits for its own Done button, so the production payment prompt of a tactical
    // action (same planet buttons, a different Done handler) is never mistaken for it.
    @Test
    void ignoresAProductionPaymentPromptWhileAnObjectivePaymentIsPending() {
        PaymentRules.expect(
                test.context(),
                "an objective",
                new Wallet.Payment(List.of("mordaiii"), List.of(), 0, 0),
                PaymentRules.OBJECTIVE_DONE);
        AiPrompt production = prompt(
                "production",
                PromptSource.PUBLIC,
                NOW,
                List.of("spend_mordaiii_res", "deleteButtons_tacticalAction"),
                List.of("Mordai II", DONE));

        assertThat(PaymentRules.pay(test.context(production))).isEmpty();
    }

    private Optional<String> pressed(AiPrompt prompt) {
        return PaymentRules.pay(test.context(prompt)).map(AiTestGame::pressedId);
    }
}
