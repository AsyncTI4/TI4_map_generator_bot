package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;

class MatchDescriberTest {

    @Test
    void threadTitleUsesCompactExpansionAndLowercasePace() {
        MatchedGame game =
                game(MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION, "10", "Average (30 days)", List.of(), List.of());
        assertThat(MatchDescriber.threadTitle(game)).isEqualTo("6p, 10vp, PoK + TE, average (30 days) pace");
    }

    @Test
    void threadTitleLeadsWithTiglRanksWhenPresent() {
        MatchedGame game = game(
                MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION,
                "10",
                "Average (30 days)",
                List.of(),
                List.of("Agent", "Commander"));
        assertThat(MatchDescriber.threadTitle(game))
                .isEqualTo("Agent/Commander: 6p, 10vp, PoK + TE, average (30 days) pace");
    }

    private static MatchedGame game(
            String expansion, String victoryPoints, String pace, List<String> restrictions, List<String> tiglRanks) {
        return new MatchedGame(List.of(), List.of(), "6", victoryPoints, expansion, pace, restrictions, tiglRanks);
    }
}
