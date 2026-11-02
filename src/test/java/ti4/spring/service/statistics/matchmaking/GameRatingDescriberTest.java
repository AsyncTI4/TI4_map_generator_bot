package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameRatingDescriberTest {

    @Test
    void listsEachPlayerWithAverageTierAndMatchQuality() {
        Map<String, String> usernames = new LinkedHashMap<>();
        usernames.put("1", "alice");
        usernames.put("2", "bob");
        Map<String, MatchmakingRating> ratings = Map.of("1", rating("1", "alice", "22.1", "100"));

        String description = GameRatingDescriber.describe(new GameRatingDescriber.GameRatings(
                "pbd123", usernames, ratings, 2105, 2000, SkillTier.MEDIUM, 0.4321));

        assertThat(description)
                .contains("pbd123")
                .containsPattern("alice\s+2210\s+100%")
                .containsPattern("bob\s+unrated")
                .contains("Average MMR: `2105` (1 unrated player counted at the player-base average of `2000`)")
                .contains("Skill tier: Medium (1900-2149)")
                .contains("Match quality: `43.2%`");
        // alice is listed before bob because the table keeps seat order
        assertThat(description.indexOf("alice")).isLessThan(description.indexOf("bob"));
    }

    @Test
    void omitsUnratedNoteWhenEveryoneIsRated() {
        Map<String, String> usernames = new LinkedHashMap<>();
        usernames.put("1", "alice");
        Map<String, MatchmakingRating> ratings = Map.of("1", rating("1", "alice", "22.1", "100"));

        String description = GameRatingDescriber.describe(
                new GameRatingDescriber.GameRatings("pbd123", usernames, ratings, 2210, 2000, null, 1.0));

        assertThat(description).contains("Average MMR: `2210`\n").contains("Skill tier: none");
    }

    private static MatchmakingRating rating(String userId, String username, String rating, String calibration) {
        return new MatchmakingRating(
                userId, username, new BigDecimal(rating), BigDecimal.ONE, new BigDecimal(calibration), 0L, null);
    }
}
