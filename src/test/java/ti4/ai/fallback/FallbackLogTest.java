package ti4.ai.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.testUtils.BaseTi4Test;

class FallbackLogTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.game.setRound(3);
    }

    // The record carries what an investigator needs: where in the game, which seat, the prompt, why the seat fell
    // back, what it had been doing on that prompt, the decision it was about to make and what the fallback did.
    @Test
    void aRecordedFallbackKeepsTheGameSeatPromptReasonAndRecentDecisions() {
        AiPrompt prompt = prompt(
                PromptSource.PUBLIC,
                "Pick a system, then say yes, or no: a=b",
                "FFCC_nekro_ringTile_301",
                "FFCC_nekro_deleteButtons");

        FallbackLog.record(
                test.game,
                FallbackLog.capture(
                        test.game,
                        test.nekro,
                        prompt,
                        "NO_PROGRESS",
                        "its choice made no progress",
                        List.of("press a (first)", "press b (second)"),
                        "press a (third)",
                        "picked Done",
                        1234L));

        List<FallbackRecord> records = FallbackLog.recent(test.game);
        assertThat(records).hasSize(1);
        FallbackRecord record = records.getFirst();
        assertThat(record.game()).isEqualTo("ai-test-game");
        assertThat(record.round()).isEqualTo(3);
        assertThat(record.phase()).isEqualTo("action");
        assertThat(record.faction()).isEqualTo("nekro");
        assertThat(record.color()).isEqualTo("black");
        assertThat(record.kind()).isEqualTo("NO_PROGRESS");
        assertThat(record.reason()).isEqualTo("its choice made no progress");
        assertThat(record.promptMessageId()).isEqualTo("prompt");
        assertThat(record.promptText()).isEqualTo("Pick a system, then say yes, or no: a=b");
        assertThat(record.buttonIds()).containsExactly("FFCC_nekro_ringTile_301", "FFCC_nekro_deleteButtons");
        assertThat(record.recentDecisions()).containsExactly("press a (first)", "press b (second)");
        assertThat(record.pendingDecision()).isEqualTo("press a (third)");
        assertThat(record.outcome()).isEqualTo("picked Done");
        assertThat(record.atMillis()).isEqualTo(1234L);
    }

    @Test
    void theLogLineNamesEveryField() {
        AiPrompt prompt = prompt(PromptSource.PUBLIC, "Choose", "FFCC_nekro_deleteButtons");
        FallbackRecord record = FallbackLog.capture(
                test.game, test.nekro, prompt, "UNSURE", "no rule", List.of("a"), "unsure (no rule)", "asked", 1L);

        assertThat(record.logLine())
                .startsWith(FallbackLog.LINE_PREFIX)
                .contains("game=ai-test-game", "round=3", "phase=action", "seat=nekro/black", "kind=UNSURE")
                .contains("reason=\"no rule\"", "prompt=prompt", "buttons=[FFCC_nekro_deleteButtons]")
                .contains("recent=[a]", "pending=\"unsure (no rule)\"", "outcome=\"asked\"");
    }

    // Private prompts name cards and secrets, so only their button handlers are kept, never their text.
    @Test
    void aPrivatePromptKeepsNoTextAndOnlyHandlerNames() {
        AiPrompt prompt =
                prompt(PromptSource.AI_THREAD, "Your secret is Become a Legend", "FFCC_nekro_discardSecret_2");

        FallbackRecord record =
                FallbackLog.capture(test.game, test.nekro, prompt, "UNSURE", "x", List.of(), "", "y", 1L);

        assertThat(record.privatePrompt()).isTrue();
        assertThat(record.promptText()).doesNotContain("Legend");
        assertThat(record.buttonIds()).containsExactly("discardSecret_2");
    }

    @Test
    void aTimeoutWithoutAPromptIsRecordedToo() {
        FallbackRecord record = FallbackLog.capture(
                test.game, test.nekro, null, "TIMEOUT", "waiting on its turn", List.of(), "", "stuck", 1L);

        FallbackLog.record(test.game, record);

        assertThat(FallbackLog.recent(test.game)).containsExactly(record);
    }

    @Test
    void recordsSurviveTheSaveFormatThatSplitsStoredValuesOnCommasAndColons() {
        FallbackRecord record = FallbackLog.capture(
                test.game,
                test.nekro,
                prompt(PromptSource.PUBLIC, "a, b: c; d\ne", "FFCC_nekro_x"),
                "UNSURE",
                "r, s: t",
                List.of("p, q: r"),
                "",
                "o",
                9L);

        FallbackLog.record(test.game, record);

        test.game.getStoredValueMap().forEach((key, value) -> {
            if (FallbackLog.isLogKey(key)) assertThat(value).doesNotContain(",", ":", ";", "\n");
        });
        assertThat(FallbackLog.recent(test.game).getFirst().promptText()).isEqualTo("a, b: c; d e");
    }

    @Test
    void keepsOnlyTheMostRecentTenNewestFirst() {
        for (int i = 1; i <= 13; i++) {
            FallbackLog.record(
                    test.game,
                    FallbackLog.capture(test.game, test.nekro, null, "UNSURE", "reason " + i, List.of(), "", "o", i));
        }

        List<FallbackRecord> records = FallbackLog.recent(test.game);

        assertThat(records).hasSize(10);
        assertThat(records.getFirst().reason()).isEqualTo("reason 13");
        assertThat(records.getLast().reason()).isEqualTo("reason 4");
    }

    private static AiPrompt prompt(PromptSource source, String content, String... customIds) {
        List<PromptButton> buttons = new ArrayList<>();
        for (int i = 0; i < customIds.length; i++) {
            buttons.add(PromptButton.of(i, Button.secondary(customIds[i], "Option " + i)));
        }
        return new AiPrompt("channel", "prompt", source, content, buttons, AiTestGame.NOW);
    }
}
