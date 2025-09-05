package ti4.service.statistics.matchmaking;

import com.pocketcombats.openskill.Adjudicator;
import com.pocketcombats.openskill.RatingModelConfig;
import com.pocketcombats.openskill.data.PlayerResult;
import com.pocketcombats.openskill.data.SimplePlayerResult;
import com.pocketcombats.openskill.data.SimpleTeamResult;
import com.pocketcombats.openskill.data.TeamResult;
import com.pocketcombats.openskill.model.BradleyTerryFull;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
class OpenSkillMatchmakingRatingService {

    private static final double STARTING_SIGMA = 8.333333333333334;
    private static final double STARTING_MU = 25;
    private static final double SIGMA_CALIBRATION_THRESHOLD = 4;

    static List<MatchmakingRating> calculateRatings(List<MatchmakingGame> games) {
        games.sort(Comparator.comparingLong(MatchmakingGame::endedDate).thenComparing(MatchmakingGame::name));

        RatingModelConfig config = RatingModelConfig.builder().build();
        Adjudicator<String> adjudicator = new Adjudicator<>(config, new BradleyTerryFull(config));

        Map<String, PlayerResult<String>> userIdsToRatings = new HashMap<>();
        Map<String, String> userIdToUsername = new HashMap<>();

        for (MatchmakingGame game : games) {
            List<MatchmakingPlayer> players = game.players();
            List<TeamResult<String>> teams = players.stream()
                    .map(player -> {
                        PlayerResult<String> rating = userIdsToRatings.computeIfAbsent(
                                player.userId(),
                                k -> new SimplePlayerResult<>(player.userId(), STARTING_MU, STARTING_SIGMA));
                        userIdToUsername.put(player.userId(), player.username());
                        return mapPlayerToTeam(rating, player);
                    })
                    .toList();
            adjudicator.rate(teams).forEach(adjustment -> {
                String userId = adjustment.playerId();
                userIdsToRatings.put(
                        userId, new SimplePlayerResult<>(adjustment.playerId(), adjustment.mu(), adjustment.sigma()));
            });
        }

        return buildPlayerRatings(userIdsToRatings, userIdToUsername);
    }

    private static TeamResult<String> mapPlayerToTeam(PlayerResult<String> rating, MatchmakingPlayer player) {
        return new SimpleTeamResult<>(rating.mu(), rating.sigma(), player.rank(), List.of(rating));
    }

    private static List<MatchmakingRating> buildPlayerRatings(
            Map<String, PlayerResult<String>> ratings, Map<String, String> userIdToUsername) {
        return ratings.entrySet().stream()
                .map(entry -> {
                    String userId = entry.getKey();
                    String username = userIdToUsername.get(userId);
                    PlayerResult<String> rating = entry.getValue();
                    double calibrationPercent =
                        Math.min(100, SIGMA_CALIBRATION_THRESHOLD / rating.sigma() * 100);
                    return new MatchmakingRating(userId, username, rating.mu(), calibrationPercent);
                })
                .sorted(Comparator.comparing(MatchmakingRating::rating).reversed())
                .toList();
    }
}
