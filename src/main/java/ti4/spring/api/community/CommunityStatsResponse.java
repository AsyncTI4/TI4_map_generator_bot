package ti4.spring.api.community;

import java.util.List;

public record CommunityStatsResponse(
        long activeGames,
        Long turnsThisWeek,
        long players,
        long gamesCompleted,
        long generatedAtEpochMs,
        long ttlSeconds,
        List<String> unavailableMetrics) {}
