package ti4.spring.service.statistics.matchmaking.queue;

import java.util.List;

public record PlayerSearchCriteria(
        List<String> playerCounts,
        List<String> victoryPointGoals,
        List<String> expansions,
        List<String> paces,
        List<String> restrictions,
        boolean tigl,
        List<String> tiglRanks) {}
