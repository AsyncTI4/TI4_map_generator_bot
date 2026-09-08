package ti4.spring.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.logging.BotLogger;
import ti4.spring.service.persistence.MatchmakingRatingEntity;
import ti4.spring.service.persistence.MatchmakingRatingEntityRepository;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class MatchmakingRatingCalculationService {

    private static final double UNRATED_MEAN = 25.0;

    private final PlayerEntityRepository playerEntityRepository;
    private final MatchmakingRatingEntityRepository matchmakingRatingEntityRepository;

    public void recalculateAndStore() {
        ToDoubleFunction<String> previousRatings = previousMeanRatings();
        var rankProvider = new SimulatedGameRankProvider(previousRatings);

        List<MatchmakingRatingEntity> rows = new ArrayList<>();
        for (boolean tiglOnly : new boolean[] {false, true}) {
            rows.addAll(calculate(tiglOnly, rankProvider));
        }

        matchmakingRatingEntityRepository.deleteAllInBatch();
        matchmakingRatingEntityRepository.saveAll(rows);
        BotLogger.info(String.format("Persisted %,d matchmaking rating rows.", rows.size()));
    }

    private List<MatchmakingRatingEntity> calculate(boolean tiglOnly, SimulatedGameRankProvider rankProvider) {
        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByCompletedNonAllianceGame(tiglOnly);
        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players, rankProvider::getRanks);

        List<MatchmakingRating> meanRatings =
                TrueSkillMatchmakingRatingService.calculateRatings(new ArrayList<>(games), false);
        List<MatchmakingRating> conservativeRatings =
                TrueSkillMatchmakingRatingService.calculateRatings(new ArrayList<>(games), true);

        Map<String, MatchmakingRating> conservativeByUserId = new HashMap<>();
        conservativeRatings.forEach(rating -> conservativeByUserId.put(rating.userId(), rating));

        long calculatedAt = System.currentTimeMillis();
        List<MatchmakingRatingEntity> rows = new ArrayList<>();
        for (MatchmakingRating meanRating : meanRatings) {
            MatchmakingRating conservative = conservativeByUserId.get(meanRating.userId());
            if (conservative == null) {
                continue;
            }
            rows.add(toEntity(meanRating, conservative, tiglOnly, calculatedAt));
        }
        return rows;
    }

    private static MatchmakingRatingEntity toEntity(
            MatchmakingRating meanRating, MatchmakingRating conservativeRating, boolean tiglOnly, long calculatedAt) {
        var entity = new MatchmakingRatingEntity();
        entity.setUserId(meanRating.userId());
        entity.setUsername(meanRating.username());
        entity.setTiglOnly(tiglOnly);
        entity.setMeanRating(meanRating.rating().doubleValue());
        entity.setConservativeRating(conservativeRating.rating().doubleValue());
        entity.setSigma(meanRating.sigma().doubleValue());
        entity.setCalibrationPercent(meanRating.calibrationPercent().doubleValue());
        entity.setLastGameEndedEpochMilliseconds(meanRating.lastGameEndedDate());
        entity.setRecentMeanDelta(
                meanRating.recentRatingDelta() == null
                        ? null
                        : meanRating.recentRatingDelta().doubleValue());
        entity.setRecentConservativeDelta(
                conservativeRating.recentRatingDelta() == null
                        ? null
                        : conservativeRating.recentRatingDelta().doubleValue());
        entity.setCalculatedEpochMilliseconds(calculatedAt);
        return entity;
    }

    private ToDoubleFunction<String> previousMeanRatings() {
        Map<String, Double> ratings = new HashMap<>();
        matchmakingRatingEntityRepository
                .findAllByTiglOnly(false)
                .forEach(row -> ratings.put(row.getUserId(), row.getMeanRating()));
        return userId -> ratings.getOrDefault(userId, UNRATED_MEAN);
    }
}
