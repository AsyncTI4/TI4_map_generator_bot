package ti4.spring.service.statistics.matchmaking.queue;

import java.util.List;
import ti4.settings.users.UserSettings;

record QueuedParty(MatchmakingQueueParty party, List<MatchmakingQueueMember> members, UserSettings leaderSettings) {

    int size() {
        return members.size();
    }
}
