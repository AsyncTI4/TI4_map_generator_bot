package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;

record MatchmakingRating(String userId, String username, double rating, BigDecimal calibrationPercent) {}
