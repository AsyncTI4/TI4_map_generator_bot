package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;

public record MatchmakingRating(String userId, String username, BigDecimal rating, BigDecimal calibrationPercent) {}
