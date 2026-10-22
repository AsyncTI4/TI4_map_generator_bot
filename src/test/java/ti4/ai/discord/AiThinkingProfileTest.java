package ti4.ai.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.trace.ThinkingProfile;
import ti4.ai.trace.ThinkingProfile.SpanTotal;
import ti4.ai.trace.ThinkingStats;
import ti4.testUtils.BaseTi4Test;

class AiThinkingProfileTest extends BaseTi4Test {

    // Timings of the other seats say what is on the board, so a Fog of War table only gets them from a developer.
    @Test
    void aFogOfWarGameHidesTheProfileFromEveryoneButDevelopers() {
        AiTestGame test = new AiTestGame();
        test.game.setFowMode(true);

        assertThat(AiThinkingProfile.describe(test.game, false)).contains("hidden in Fog of War");
        assertThat(AiThinkingProfile.describe(test.game, true)).doesNotContain("hidden in Fog of War");
    }

    @Test
    void aGameWithoutDecisionsSaysSo() {
        AiTestGame test = new AiTestGame();

        assertThat(AiThinkingProfile.describe(test.game, false)).contains("not made a decision");
    }

    // One message is at most 2000 characters, however many spans there are or however long their names get.
    @Test
    void theProfileFitsInOneDiscordMessage() {
        ThinkingStats stats = new ThinkingStats();
        List<SpanTotal> spans = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            spans.add(new SpanTotal("a.very.long.span.name.number." + i + "x".repeat(60), i, i, i, 1));
        }
        for (int i = 0; i < 20; i++) {
            stats.record(new ThinkingProfile(5_000_000_000L, spans), "faction" + "y".repeat(30), "cut short", true);
        }

        assertThat(AiThinkingProfile.render(stats)).hasSizeLessThanOrEqualTo(AiThinkingProfile.MAX_LENGTH);
        assertThat(AiThinkingProfile.render(stats).lines().count()).isLessThanOrEqualTo(12);
    }
}
