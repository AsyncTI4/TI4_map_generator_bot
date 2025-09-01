package ti4.service.statistics.matchmaking.openskill;

import io.github.pocketcombats.openskill.OpenSkill;
import io.github.pocketcombats.openskill.Rating;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.map.persistence.GameManager;
import ti4.service.statistics.matchmaking.MatchmakingGame;
import ti4.service.statistics.matchmaking.MatchmakingPlayer;
import ti4.service.statistics.matchmaking.MatchmakingRating;

/** Utility service implementing matchmaking rating using the OpenSkill algorithm. */
@UtilityClass
public class MatchmakingRatingService {

    private static final double SIGMA_CALIBRATION_THRESHOLD = 1;

    /**
     * Calculate player ratings using the OpenSkill library.
     *
     * @param games list of games to process
     * @return list of calculated player ratings
     */
    public static List<MatchmakingRating> calculateRatings(List<MatchmakingGame> games) {
        Map<String, Rating> ratings = new HashMap<>();
        OpenSkill openSkill = new OpenSkill();

        games.sort(
                Comparator.comparingLong(MatchmakingGame::endedDate)
                        .thenComparing(MatchmakingGame::name));

        for (MatchmakingGame game : games) {
            List<MatchmakingPlayer> gamePlayers = game.players();
            List<List<Rating>> teams = new ArrayList<>();
            int[] ranks = new int[gamePlayers.size()];
            for (int i = 0; i < gamePlayers.size(); i++) {
                MatchmakingPlayer gamePlayer = gamePlayers.get(i);
                Rating rating = ratings.computeIfAbsent(gamePlayer.userId(), id -> openSkill.rating());
                teams.add(Collections.singletonList(rating));
                ranks[i] = gamePlayer.rank();
            }
            List<List<Rating>> newRatings = openSkill.rate(teams, ranks);
            for (int i = 0; i < gamePlayers.size(); i++) {
                ratings.put(gamePlayers.get(i).userId(), newRatings.get(i).get(0));
            }
        }

        return buildPlayerRatings(ratings);
    }

    private static List<MatchmakingRating> buildPlayerRatings(Map<String, Rating> ratings) {
        return ratings.entrySet().stream()
                .map(
                        entry -> {
                            Rating rating = entry.getValue();
                            String username =
                                    GameManager.getManagedPlayer(entry.getKey()).getName();
                            double calibrationPercent =
                                    SIGMA_CALIBRATION_THRESHOLD / rating.sigma() * 100;
                            return new MatchmakingRating(
                                    entry.getKey(), username, rating.mu(), calibrationPercent);
                        })
                .sorted(Comparator.comparing(MatchmakingRating::rating).reversed())
                .toList();
    }
}

