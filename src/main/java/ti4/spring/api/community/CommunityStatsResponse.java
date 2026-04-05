package ti4.spring.api.community;

import java.util.List;

public record CommunityStatsResponse(
        long activeGames,
        long players,
        long gamesCompleted,
        List<CommunityGameSample> gamesInProgress,
        long generatedAtEpochMs,
        long ttlSeconds,
        List<String> unavailableMetrics) {}
