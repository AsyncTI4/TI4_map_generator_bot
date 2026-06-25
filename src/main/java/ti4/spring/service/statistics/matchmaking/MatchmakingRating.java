package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;

record MatchmakingRating(
        String userId, String username, BigDecimal rating, BigDecimal sigma, BigDecimal calibrationPercent) {}
