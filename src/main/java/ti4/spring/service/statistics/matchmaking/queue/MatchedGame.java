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
        // The shared TIGL rank of the matched players, or null when this isn't a TIGL game.
        String tiglRank) {}
