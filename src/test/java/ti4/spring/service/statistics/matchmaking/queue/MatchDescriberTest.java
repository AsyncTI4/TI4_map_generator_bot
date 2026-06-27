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
    void threadTitleAppendsOnlyCuratedRestrictions() {
        MatchedGame game = game(
                MatchmakingOptions.FRANKEN_EXPANSION_OPTION,
                "12",
                "Slower",
                List.of(
                        MatchmakingOptions.SIMILAR_ACTIVE_HOURS_OPTION,
                        MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION,
                        MatchmakingOptions.AVOID_NEW_PLAYERS_OPTION,
                        MatchmakingOptions.TIGL_OPTION,
                        MatchmakingOptions.ONLY_MATCH_FLOATERS_OPTION),
                "Archon");
        assertThat(MatchDescriber.threadTitle(game))
                .isEqualTo("6p, 12vp, Franken, slower pace, similar timezone, TIGL (Archon), Floaters");
    }

    private static MatchedGame game(
            String expansion, String victoryPoints, String pace, List<String> restrictions, String tiglRank) {
        return new MatchedGame(List.of(), List.of(), "6", victoryPoints, expansion, pace, restrictions, tiglRank);
    }
}
