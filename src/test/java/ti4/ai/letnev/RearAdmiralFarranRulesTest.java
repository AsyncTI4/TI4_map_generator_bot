package ti4.ai.letnev;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// Rear Admiral Farran's trade good is optional inside Pillage range, and free: the seat takes it and the prompt does
// not stall the table.
class RearAdmiralFarranRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    private AiPrompt offer(String mentioning) {
        AiPrompt offer = prompt(
                "offer",
                PromptSource.PUBLIC,
                NOW,
                List.of("gain1tgFromLetnevCommander", "deleteButtons"),
                List.of("Gain 1 Trade Good", "Decline"));
        return AiTestGame.withContent(
                offer,
                mentioning
                        + ", you have Rear Admiral Farran, the Letnev commander, unlocked, so you may gain 1 trade good.");
    }

    @Test
    void takesTheTradeGood() {
        AiPrompt offer = offer(test.nekro.getRepresentationUnfogged());

        Optional<AiDecision> decision = RearAdmiralFarranRules.gainTradeGood(test.context(offer));

        assertThat(decision.map(AiTestGame::pressedId)).contains("gain1tgFromLetnevCommander");
    }

    @Test
    void leavesAnotherPlayersOfferAlone() {
        AiPrompt offer = offer(test.sol.getRepresentationUnfogged());

        assertThat(RearAdmiralFarranRules.gainTradeGood(test.context(offer))).isEmpty();
    }

    @Test
    void doesNotPressTheSameOfferTwice() {
        AiPrompt offer = offer(test.nekro.getRepresentationUnfogged());
        String key = ti4.ai.brain.AiTurnContext.pressKey(offer, offer.buttons().getFirst());

        assertThat(RearAdmiralFarranRules.gainTradeGood(test.context(Set.of(key), offer)))
                .isEmpty();
    }

    @Test
    void theBrainAnswersTheOfferInThePublicChannel() {
        assertThat(new NekroBrain().publicWindowHandlerPrefixes()).contains(RearAdmiralFarranRules.GAIN);
    }
}
