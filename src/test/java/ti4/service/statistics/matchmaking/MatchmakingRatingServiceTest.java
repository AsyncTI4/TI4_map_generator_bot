package ti4.service.statistics.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

class MatchmakingRatingServiceTest {

  @Test
  void sixPlayerGameWinnerBestRating() {
    MatchmakingGame game = new MatchmakingGame(
        "game1",
        1L,
        Lists.newArrayList(
            new MatchmakingPlayer("p1", "player1", 1),
            new MatchmakingPlayer("p2", "player2", 2),
            new MatchmakingPlayer("p3", "player3", 2),
            new MatchmakingPlayer("p4", "player4", 3),
            new MatchmakingPlayer("p5", "player5", 3),
            new MatchmakingPlayer("p6", "player6", 3)));

    List<MatchmakingRating> ratings = MatchmakingRatingService.calculateRatings(Lists.newArrayList(game));

    List<MatchmakingRating> sortedRatings = ratings.stream()
        .sorted(Comparator.comparing(MatchmakingRating::rating).reversed().thenComparing(MatchmakingRating::userId))
        .toList();

    assertThat(ratings).hasSize(6);
    assertThat(sortedRatings).isSortedAccordingTo(Comparator.comparing(MatchmakingRating::userId));

    double winnerRating = ratings.get(0).rating();
    double rank2Rating = ratings.get(1).rating();
    assertThat(winnerRating).isGreaterThan(rank2Rating);

    double otherRank2Rating = ratings.get(2).rating();
    assertThat(otherRank2Rating).isEqualTo(rank2Rating);

    double rank3Rating = ratings.get(3).rating();
    assertThat(rank3Rating).isLessThan(rank2Rating);
  }

  @Test
  void consistentPlayerOutperformsOthersAndCalibrated() {
    List<MatchmakingGame> games = new ArrayList<>();

    games.add(new MatchmakingGame("g1", 1L, List.of(
        new MatchmakingPlayer("p1", "player1", 1),
        new MatchmakingPlayer("p2", "player2", 2),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g2", 2L, List.of(
        new MatchmakingPlayer("p1", "player1", 1),
        new MatchmakingPlayer("p2", "player2", 2),
        new MatchmakingPlayer("p3", "player3", 2),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g3", 3L, List.of(
        new MatchmakingPlayer("p1", "player1", 2),
        new MatchmakingPlayer("p2", "player2", 1),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g4", 4L, List.of(
        new MatchmakingPlayer("p1", "player1", 2),
        new MatchmakingPlayer("p2", "player2", 3),
        new MatchmakingPlayer("p3", "player3", 1),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g5", 5L, List.of(
        new MatchmakingPlayer("p1", "player1", 2),
        new MatchmakingPlayer("p2", "player2", 3),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 1),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g6", 6L, List.of(
        new MatchmakingPlayer("p1", "player1", 3),
        new MatchmakingPlayer("p2", "player2", 3),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 1),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g7", 7L, List.of(
        new MatchmakingPlayer("p1", "player1", 3),
        new MatchmakingPlayer("p2", "player2", 3),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 1))));
    games.add(new MatchmakingGame("g8", 8L, List.of(
        new MatchmakingPlayer("p1", "player1", 3),
        new MatchmakingPlayer("p2", "player2", 3),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 1),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g9", 9L, List.of(
        new MatchmakingPlayer("p1", "player1", 3),
        new MatchmakingPlayer("p2", "player2", 3),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 1),
        new MatchmakingPlayer("p6", "player6", 3))));
    games.add(new MatchmakingGame("g10", 10L, List.of(
        new MatchmakingPlayer("p1", "player1", 3),
        new MatchmakingPlayer("p2", "player2", 3),
        new MatchmakingPlayer("p3", "player3", 3),
        new MatchmakingPlayer("p4", "player4", 3),
        new MatchmakingPlayer("p5", "player5", 3),
        new MatchmakingPlayer("p6", "player6", 1))));

    List<MatchmakingRating> ratings = MatchmakingRatingService.calculateRatings(games);
    Map<String, MatchmakingRating> ratingMap =
        ratings.stream().collect(Collectors.toMap(MatchmakingRating::userId, r -> r));

    MatchmakingRating p1 = ratingMap.get("p1");
    assertThat(p1.rating()).isGreaterThan(ratingMap.get("p2").rating());
    assertThat(p1.rating()).isGreaterThan(ratingMap.get("p3").rating());
    assertThat(p1.rating()).isGreaterThan(ratingMap.get("p4").rating());
    assertThat(p1.rating()).isGreaterThan(ratingMap.get("p5").rating());
    assertThat(p1.rating()).isGreaterThan(ratingMap.get("p6").rating());
    assertThat(p1.calibrationPercent()).isGreaterThan(50);
  }
}