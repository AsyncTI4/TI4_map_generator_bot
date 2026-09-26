package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;

record MatchmakingRatingHistoryEntry(
        String gameName, long endedDate, int rank, BigDecimal startRating, BigDecimal endRating) {}
