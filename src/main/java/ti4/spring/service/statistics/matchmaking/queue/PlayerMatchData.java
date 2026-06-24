package ti4.spring.service.statistics.matchmaking.queue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

record PlayerMatchData(
        String userId,
        List<String> restrictions,
        List<String> avoidList,
        BigDecimal rating,
        Set<Integer> activeHourBuckets,
        int completedGames,
        Set<String> roleNames,
        boolean halfQueueTimePassed) {}
