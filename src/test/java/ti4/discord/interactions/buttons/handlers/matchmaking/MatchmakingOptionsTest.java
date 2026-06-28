package ti4.discord.interactions.buttons.handlers.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchmakingOptionsTest {

    @Test
    void lowestTiglRankPicksTheLeastAdvancedMember() {
        assertThat(MatchmakingOptions.lowestTiglRank(List.of("Hero", "Agent", "Commander")))
                .isEqualTo("Agent");
    }

    @Test
    void lowestTiglRankTreatsNullAndBlankAsUnranked() {
        assertThat(MatchmakingOptions.lowestTiglRank(Arrays.asList("Hero", null)))
                .isEqualTo("Unranked");
        assertThat(MatchmakingOptions.lowestTiglRank(Arrays.asList("Hero", ""))).isEqualTo("Unranked");
    }

    @Test
    void lowestTiglRankOfSingleMemberIsThatMembersRank() {
        assertThat(MatchmakingOptions.lowestTiglRank(List.of("Commander"))).isEqualTo("Commander");
    }

    @Test
    void lowestTiglFracturedRankUsesTheFracturedLadder() {
        assertThat(MatchmakingOptions.lowestTiglFracturedRank(List.of("Archon", "Thrall", "Starlancer")))
                .isEqualTo("Thrall");
    }
}
