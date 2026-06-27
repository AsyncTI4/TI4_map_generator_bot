package ti4.spring.service.statistics.matchmaking.queue;

import java.util.List;

record MatchedGame(
        List<MatchmakingQueueMember> members,
        List<MatchmakingQueueParty> parties,
        String playerCount,
        String victoryPointGoal,
        String expansion,
        String pace,
        List<String> restrictions,
        String tiglRank,
        boolean needsOneMore) {}
