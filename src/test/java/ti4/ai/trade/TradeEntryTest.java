package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.testUtils.BaseTi4Test;

/**
 * Every offer starts from the unowned "Transaction" button on the cards-info message the bot re-posts each turn. The
 * AI uses a visible one, else the one it last saw, else asks the bot to post the message again.
 */
class TradeEntryTest extends BaseTi4Test {

    private static final long NOW = AiTestGame.NOW;
    private static final long REFRESH_WAIT = 20_000L;

    private TradeTable table;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
    }

    private AiTurnContext at(long now, AiPrompt... prompts) {
        return table.test.contextFor(table.nekro, Set.of(), now, prompts);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private static String pressedMessage(Optional<AiDecision> decision) {
        return decision.map(made -> ((AiDecision.Press) made).prompt().messageId())
                .orElse("");
    }

    // The button is not used up by a press (it posts a new message), so the AI may press it again; it stops after two
    // presses of the same message so that a press that went nowhere cannot repeat forever, and asks for a fresh copy
    // of the message instead.
    @Test
    void usesAVisibleTransactionButtonAtMostTwice() {
        AiPrompt various = TradeButtons.entry("various", NOW - 1_000L);

        assertThat(pressed(TradeEntry.open(at(NOW, various)))).isEqualTo("transaction");
        assertThat(pressed(TradeEntry.open(at(NOW, various)))).isEqualTo("transaction");
        assertThat(pressed(TradeEntry.open(at(NOW, various)))).isEqualTo("cardsInfo");
        assertThat(TradeEntry.open(at(NOW + REFRESH_WAIT, various))).isEmpty();
    }

    // The message scrolled out of the last 50 the AI reads: it presses the remembered button by its message id.
    @Test
    void pressesTheRememberedButtonOnceItScrolledAway() {
        AiPrompt various = TradeButtons.entry("various", NOW - 1_000L);
        TradeEntry.observe(at(NOW, various));

        Optional<AiDecision> decision = TradeEntry.open(at(NOW + 60_000L));

        assertThat(pressed(decision)).isEqualTo("transaction");
        assertThat(pressedMessage(decision)).isEqualTo("various");
        assertThat(((AiDecision.Press) decision.orElseThrow()).prompt().channelId())
                .isEqualTo(various.channelId());
    }

    // Nothing to press: "Cards Info" re-posts the message with a fresh button, and the AI waits up to 20 seconds for
    // it; the new button is pressed as soon as it shows.
    @Test
    void asksForTheMessageAgainThenWaits() {
        AiPrompt cardsInfo = TradeButtons.hidden("notes", NOW - 30_000L, List.of("cardsInfo"));

        assertThat(pressed(TradeEntry.open(at(NOW, cardsInfo)))).isEqualTo("cardsInfo");
        Optional<AiDecision> waiting = TradeEntry.open(at(NOW + 3_000L, cardsInfo));
        assertThat(waiting).containsInstanceOf(AiDecision.Wait.class);
        assertThat(((AiDecision.Wait) waiting.orElseThrow()).untilMillis()).isEqualTo(NOW + REFRESH_WAIT);

        AiPrompt reposted = TradeButtons.entry("reposted", NOW + 4_000L);
        assertThat(pressed(TradeEntry.openRefreshed(at(NOW + 6_000L, cardsInfo, reposted))))
                .isEqualTo("transaction");
    }

    // After the 20 seconds, or with no Cards Info button either, there is no way in: the draft is abandoned.
    @Test
    void givesUpWhenNothingIsAvailable() {
        AiPrompt cardsInfo = TradeButtons.hidden("notes", NOW - 30_000L, List.of("cardsInfo"));
        TradeEntry.open(at(NOW, cardsInfo));

        assertThat(TradeEntry.open(at(NOW + REFRESH_WAIT, cardsInfo))).isEmpty();

        TradeTable fresh = TradeTable.withHumanSol();
        assertThat(TradeEntry.open(fresh.test.contextFor(fresh.nekro, Set.of(), NOW)))
                .isEmpty();
    }
}
