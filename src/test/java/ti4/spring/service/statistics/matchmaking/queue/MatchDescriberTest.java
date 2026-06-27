package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

class MatchDescriberTest {

    @Test
    void threadTitleUsesCompactExpansionAndLowercasePace() {
        MatchedGame game =
                game(MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION, "10", "Average (30 days)", List.of(), null);
        assertThat(MatchDescriber.threadTitle(game)).isEqualTo("6p, 10vp, PoK + TE, average (30 days) pace");
    }

    @Test
    void nearMatchTitleIsPrefixed() {
        MatchedGame game = new MatchedGame(
                List.of(),
                List.of(),
                "6",
                "10",
                MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION,
                "Average (30 days)",
                List.of(),
                null,
                true);
        assertThat(MatchDescriber.threadTitle(game))
                .isEqualTo("NEED 1 MORE: 6p, 10vp, PoK + TE, average (30 days) pace");
    }

    private static MatchedGame game(
            String expansion, String victoryPoints, String pace, List<String> restrictions, String tiglRank) {
        return new MatchedGame(
                List.of(), List.of(), "6", victoryPoints, expansion, pace, restrictions, tiglRank, false);
    }
}
