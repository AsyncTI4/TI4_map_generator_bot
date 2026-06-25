package ti4.spring.service.statistics.matchmaking.queue;

import de.gesundkrank.jskills.Rating;
import java.util.List;
import java.util.Set;

record PlayerMatchmakingData(
        String userId,
        List<String> restrictions,
        List<String> avoidList,
        Rating rating,
        Set<Integer> activeHourBuckets,
        int completedGames,
        Set<String> roleNames,
        boolean relaxConstraints) {}
