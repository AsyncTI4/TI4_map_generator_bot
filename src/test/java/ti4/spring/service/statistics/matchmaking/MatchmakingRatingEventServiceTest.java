package ti4.spring.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.spring.service.statistics.matchmaking.TrueSkillMatchmakingRatingService.RECENT_GAMES_WINDOW;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.UserEntity;

class MatchmakingRatingEventServiceTest {

    private static final int GAMES_TO_QUALIFY = 3;
    private static final int[] RANKS_P0_WINS = {1, 2, 2, 4, 5, 5};
    private static final int[] RANKS_P4_WINS = {5, 2, 2, 4, 1, 5};
    private static final long GAME_ENDED_EPOCH_MILLIS = Instant.now().toEpochMilli();
    private static final int MANY_GAMES = 1000;
    private static final BigDecimal TIED_PLAYER_RATING_TOLERANCE_AT_QUALIFYING_GAMES = BigDecimal.valueOf(0.7);
    private static final BigDecimal TIED_PLAYER_RATING_TOLERANCE_AT_MANY_GAMES = BigDecimal.valueOf(0.05);
    private static final int VICTORY_POINT_GOAL = 10;
    private static final String SHARED_DISPLAY_NAME = "Tazingo";

    @Test
    void generatingRatingsTwiceGivesSameResult() {
        List<MatchmakingRating> sortedRatings = sortedByRating(TrueSkillMatchmakingRatingService.calculateRatings(
                buildRankedGames(GAMES_TO_QUALIFY, RANKS_P0_WINS), false));
        List<MatchmakingRating> sortedRatings2 = sortedByRating(TrueSkillMatchmakingRatingService.calculateRatings(
                buildRankedGames(GAMES_TO_QUALIFY, RANKS_P0_WINS), false));

        assertThat(sortedRatings).isEqualTo(sortedRatings2);
    }

    @Test
    void generatesSensibleRatings() {
        List<MatchmakingRating> ratings = TrueSkillMatchmakingRatingService.calculateRatings(
                buildRankedGames(GAMES_TO_QUALIFY, RANKS_P0_WINS), false);

        List<MatchmakingRating> sortedRatings = sortedByRating(ratings);

        assertThat(ratings).hasSize(6);

        BigDecimal winnerRating = sortedRatings.get(0).rating();
        BigDecimal rank2Rating = sortedRatings.get(1).rating();
        assertThat(winnerRating).isGreaterThan(rank2Rating);

        BigDecimal otherRank2Rating = sortedRatings.get(2).rating();
        assertThat(otherRank2Rating.subtract(rank2Rating).abs())
                .isLessThan(TIED_PLAYER_RATING_TOLERANCE_AT_QUALIFYING_GAMES);

        BigDecimal rank3Rating = sortedRatings.get(3).rating();
        assertThat(rank3Rating).isLessThan(rank2Rating);
    }

    @Test
    void identicallyRankedPlayersConvergeAsTheyPlayMoreGames() {
        BigDecimal gapAtQualifyingGames = tiedPlayerRatingGap(GAMES_TO_QUALIFY);
        BigDecimal gapAtManyGames = tiedPlayerRatingGap(MANY_GAMES);

        assertThat(gapAtQualifyingGames).isLessThan(TIED_PLAYER_RATING_TOLERANCE_AT_QUALIFYING_GAMES);
        assertThat(gapAtManyGames).isLessThan(gapAtQualifyingGames);
        assertThat(gapAtManyGames).isLessThan(TIED_PLAYER_RATING_TOLERANCE_AT_MANY_GAMES);
    }

    private static BigDecimal tiedPlayerRatingGap(int gameCount) {
        List<MatchmakingRating> sortedRatings = sortedByRating(
                TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(gameCount, RANKS_P0_WINS), false));
        return sortedRatings
                .get(1)
                .rating()
                .subtract(sortedRatings.get(2).rating())
                .abs();
    }

    @Test
    void excludesPlayersWithFewerThanThreeCompletedGames() {
        // Two games each means every player has only two completed games, below the three-game minimum.
        assertThat(TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(2, RANKS_P0_WINS), false))
                .isEmpty();
        assertThat(TrueSkillMatchmakingRatingService.calculateRatings(buildRankedGames(3, RANKS_P0_WINS), false))
                .hasSize(6);
    }

    @Test
    void conservativeRatingsUseConservativeTrueSkillValue() {
        List<MatchmakingRating> meanRatings = TrueSkillMatchmakingRatingService.calculateRatings(
                buildRankedGames(GAMES_TO_QUALIFY, RANKS_P0_WINS), false);
        List<MatchmakingRating> conservativeRatings = TrueSkillMatchmakingRatingService.calculateRatings(
                buildRankedGames(GAMES_TO_QUALIFY, RANKS_P0_WINS), true);

        MatchmakingRating meanWinnerRating = findRatingForUser(meanRatings, "p0");
        MatchmakingRating conservativeWinnerRating = findRatingForUser(conservativeRatings, "p0");

        assertThat(conservativeWinnerRating.rating()).isLessThan(meanWinnerRating.rating());
        assertThat(conservativeWinnerRating.calibrationPercent()).isEqualTo(meanWinnerRating.calibrationPercent());
    }

    @Test
    void reportsNoRecentTrendUntilTheWindowIsFull() {
        int gamesOneShortOfAFullWindow = RECENT_GAMES_WINDOW;
        assertThat(TrueSkillMatchmakingRatingService.calculateRatings(
                        buildRankedGames(gamesOneShortOfAFullWindow, RANKS_P0_WINS), true))
                .allSatisfy(rating -> assertThat(rating.recentRatingDelta()).isNull());

        assertThat(TrueSkillMatchmakingRatingService.calculateRatings(
                        buildRankedGames(RECENT_GAMES_WINDOW + 1, RANKS_P0_WINS), true))
                .allSatisfy(rating -> assertThat(rating.recentRatingDelta()).isNotNull());
    }

    @Test
    void recentTrendFollowsTheDirectionOfRecentResults() {
        List<MatchmakingGame> games = new ArrayList<>(buildRankedGames(30, RANKS_P0_WINS));
        for (int i = 0; i < RECENT_GAMES_WINDOW; i++) {
            games.add(buildMatchmakingGame("late" + i, RANKS_P4_WINS));
        }

        List<MatchmakingRating> ratings = TrueSkillMatchmakingRatingService.calculateRatings(games, true);

        assertThat(findRatingForUser(ratings, "p4").recentRatingDelta()).isPositive();
        assertThat(findRatingForUser(ratings, "p0").recentRatingDelta()).isNegative();
    }

    @Test
    void usersWhoShareADisplayNameAreRatedAsSeparatePlayers() {
        UserEntity tazingo = new UserEntity("tazing0", SHARED_DISPLAY_NAME);
        UserEntity otherTazingo = new UserEntity("taz", SHARED_DISPLAY_NAME);
        List<PlayerEntity> players = new ArrayList<>();
        for (int i = 0; i < GAMES_TO_QUALIFY; i++) {
            List<UserEntity> finishingOrder = new ArrayList<>();
            finishingOrder.add(tazingo);
            finishingOrder.addAll(fillerUsers(4));
            finishingOrder.add(otherTazingo);
            players.addAll(buildPlayerEntities("shared" + i, finishingOrder));
        }

        List<MatchmakingRating> ratings =
                TrueSkillMatchmakingRatingService.calculateRatings(MatchmakingGame.getMatchmakingGames(players), false);

        assertThat(ratings)
                .filteredOn(rating -> SHARED_DISPLAY_NAME.equals(rating.username()))
                .extracting(MatchmakingRating::userId)
                .containsExactlyInAnyOrder("tazing0", "taz");
        assertThat(findRatingForUser(ratings, "tazing0").rating())
                .isGreaterThan(findRatingForUser(ratings, "taz").rating());
    }

    @Test
    void doesNotPoolTheGamesOfUsersWhoShareADisplayName() {
        // Two games each: pooled by name they would clear the three-game minimum, kept apart neither does.
        UserEntity tazingo = new UserEntity("tazing0", SHARED_DISPLAY_NAME);
        UserEntity otherTazingo = new UserEntity("taz", SHARED_DISPLAY_NAME);
        List<PlayerEntity> players = new ArrayList<>();
        for (int i = 0; i < GAMES_TO_QUALIFY - 1; i++) {
            List<UserEntity> tazingoGame = new ArrayList<>(List.of(tazingo));
            tazingoGame.addAll(fillerUsers(5));
            players.addAll(buildPlayerEntities("tazing0-game" + i, tazingoGame));

            List<UserEntity> otherTazingoGame = new ArrayList<>(List.of(otherTazingo));
            otherTazingoGame.addAll(fillerUsers(5));
            players.addAll(buildPlayerEntities("taz-game" + i, otherTazingoGame));
        }

        List<MatchmakingRating> ratings =
                TrueSkillMatchmakingRatingService.calculateRatings(MatchmakingGame.getMatchmakingGames(players), false);

        assertThat(ratings).isNotEmpty();
        assertThat(ratings).noneMatch(rating -> SHARED_DISPLAY_NAME.equals(rating.username()));
    }

    private static List<UserEntity> fillerUsers(int count) {
        List<UserEntity> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(new UserEntity("filler" + i, "Filler " + i));
        }
        return users;
    }

    private static List<PlayerEntity> buildPlayerEntities(String gameName, List<UserEntity> usersInFinishingOrder) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setEndedEpochMilliseconds(GAME_ENDED_EPOCH_MILLIS);
        game.setVictoryPointGoal(VICTORY_POINT_GOAL);
        List<PlayerEntity> players = new ArrayList<>();
        for (int place = 0; place < usersInFinishingOrder.size(); place++) {
            PlayerEntity player = new PlayerEntity();
            player.setGame(game);
            player.setUser(usersInFinishingOrder.get(place));
            player.setWinner(place == 0);
            player.setScore(VICTORY_POINT_GOAL - place);
            players.add(player);
        }
        return players;
    }

    private static List<MatchmakingRating> sortedByRating(List<MatchmakingRating> ratings) {
        return ratings.stream()
                .sorted(Comparator.comparing(MatchmakingRating::rating)
                        .reversed()
                        .thenComparing(MatchmakingRating::userId))
                .toList();
    }

    @NotNull
    private static List<MatchmakingGame> buildRankedGames(int gameCount, int[] ranks) {
        List<MatchmakingGame> games = new ArrayList<>();
        for (int i = 0; i < gameCount; i++) {
            games.add(buildMatchmakingGame("game" + i, ranks));
        }
        return games;
    }

    @NotNull
    private static MatchmakingGame buildMatchmakingGame(String name, int[] ranks) {
        var players = new ArrayList<MatchmakingPlayer>();
        for (int i = 0; i < ranks.length; i++) {
            players.add(new MatchmakingPlayer("p" + i, "player" + i, ranks[i]));
        }
        return new MatchmakingGame(name, GAME_ENDED_EPOCH_MILLIS, players);
    }

    private static MatchmakingRating findRatingForUser(List<MatchmakingRating> ratings, String userId) {
        return ratings.stream()
                .filter(rating -> rating.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
