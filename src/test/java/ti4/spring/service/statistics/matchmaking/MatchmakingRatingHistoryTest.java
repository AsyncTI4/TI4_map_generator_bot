package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.message.MessageHelper;

class MatchmakingRatingHistoryTest {

    private static final String TRACKED_USER_ID = "u0";
    private static final long DAY_MILLIS = 86_400_000L;

    @Test
    void finalHistoryRatingMatchesLadderRating() {
        List<MatchmakingRatingHistoryEntry> history =
                TrueSkillMatchmakingRatingService.calculateRatingHistory(buildGames(20), TRACKED_USER_ID, true);
        MatchmakingRating ladderRating =
                TrueSkillMatchmakingRatingService.calculateRatings(buildGames(20), true).stream()
                        .filter(rating -> TRACKED_USER_ID.equals(rating.userId()))
                        .findFirst()
                        .orElseThrow();

        assertThat(history.getLast().endRating()).isEqualTo(ladderRating.rating());
    }

    @Test
    void eachGameStartsWhereThePreviousGameEnded() {
        List<MatchmakingRatingHistoryEntry> history =
                TrueSkillMatchmakingRatingService.calculateRatingHistory(buildGames(20), TRACKED_USER_ID, true);

        // The tracked player sits out every third game, so those games must not appear in their history.
        assertThat(history).hasSize(14);
        for (int i = 1; i < history.size(); i++) {
            assertThat(history.get(i).startRating())
                    .isEqualTo(history.get(i - 1).endRating());
        }
    }

    @Test
    void longHistoriesSplitIntoCompleteCodeBlocksWithinDiscordLimits() {
        List<MatchmakingRatingHistoryEntry> history =
                TrueSkillMatchmakingRatingService.calculateRatingHistory(buildGames(300), TRACKED_USER_ID, true);

        List<String> messages = MessageHelper.packBlocksIntoMessages(
                MatchmakingRatingEventService.ratingHistoryBlocks("Tazingo", history), 2000);

        assertThat(messages).hasSizeGreaterThan(1);
        for (String message : messages) {
            assertThat(message.length()).isLessThanOrEqualTo(2000);
            // An odd number of fences would leave a code block open across messages.
            assertThat(message.split("```", -1).length % 2).isEqualTo(1);
            assertThat(message).contains("Game").contains("Change");
        }
    }

    private static List<MatchmakingGame> buildGames(int gameCount) {
        List<MatchmakingGame> games = new ArrayList<>();
        for (int gameIndex = 0; gameIndex < gameCount; gameIndex++) {
            List<MatchmakingPlayer> players = new ArrayList<>();
            int firstUser = gameIndex % 3 == 2 ? 1 : 0;
            for (int seat = 0; seat < 6; seat++) {
                String userId = "u" + (firstUser + seat);
                int rank = (seat + gameIndex) % 6 == 0 ? 1 : 2 + (seat + gameIndex) % 6 * 2;
                players.add(new MatchmakingPlayer(userId, userId, rank));
            }
            games.add(new MatchmakingGame("pbd" + (1000 + gameIndex), gameIndex * DAY_MILLIS, players));
        }
        return games;
    }
}
