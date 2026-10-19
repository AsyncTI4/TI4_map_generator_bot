package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// After a rejected offer the AI makes no new offer to that player this round. If it was asking a human for a
// promissory note, it asks what they would want instead, and a reply that costs too much drops the note deal until
// a round has passed.
class OfferRejectionsTest extends BaseTi4Test {

    private static final String SOL_THRONE = "blue_sftt";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.game.setRound(3);
    }

    // The bot posts this in the offerer's private thread when the other player presses Reject.
    private AiPrompt rejectionNotice() {
        String content = test.nekro.getRepresentation() + " your offer to " + test.sol.getRepresentation(false, false)
                + " has been rejected.";
        return new AiPrompt("thread", "notice", PromptSource.AI_THREAD, content, List.of(), NOW);
    }

    @Test
    void makesNoNewOfferToThatPlayerThisRound() {
        AiTurnContext context = test.context(rejectionNotice());
        OfferRejections.observe(context);

        assertThat(OfferRejections.coolingDown(context, test.sol)).isTrue();
        assertThat(OwnTurnDeals.mayApproach(context, test.sol)).isFalse();
        test.game.setRound(4);
        assertThat(OfferRejections.coolingDown(context, test.sol)).isFalse();
    }

    @Test
    void asksAHumanWhatTheyWantForTheNote() {
        OfferRejections.noteAsked(test.memory, test.sol, SOL_THRONE);
        AiTurnContext context = test.context(rejectionNotice());
        OfferRejections.observe(context);

        AiDecision decision = OfferRejections.askWhatTheyWant(context).orElseThrow();

        assertThat(decision)
                .isInstanceOfSatisfying(
                        AiDecision.Announce.class,
                        announce -> assertThat(announce.text())
                                .contains("what would you want for your _Support for the Throne_"));
        assertThat(OfferRejections.askWhatTheyWant(context)).isEmpty();
        assertThat(OfferRejections.answering(context, test.sol)).isTrue();
    }

    // Their answer costs more than the note is worth: the note deal waits until the next round.
    @Test
    void dropsTheNoteDealUntilARoundHasPassedWhenTheAnswerCostsTooMuch() {
        OfferRejections.noteAsked(test.memory, test.sol, SOL_THRONE);
        AiTurnContext context = test.context(rejectionNotice());
        OfferRejections.observe(context);
        OfferRejections.askWhatTheyWant(context);

        OfferRejections.answered(context, test.sol, false);

        assertThat(OfferRejections.answering(context, test.sol)).isFalse();
        assertThat(OfferRejections.dropped(context, test.sol)).isTrue();
        test.game.setRound(4);
        assertThat(OfferRejections.dropped(context, test.sol)).isFalse();
    }

    // The same notice seen again on a later tick is not a second rejection.
    @Test
    void readsEachNoticeOnce() {
        AiTurnContext context = test.context(rejectionNotice());
        OfferRejections.observe(context);
        test.game.setRound(4);

        OfferRejections.observe(test.context(rejectionNotice()));

        assertThat(OfferRejections.coolingDown(context, test.sol)).isFalse();
    }
}
