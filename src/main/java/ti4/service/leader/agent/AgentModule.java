package ti4.service.leader.agent;

import java.util.Optional;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;

public interface AgentModule<P> {

    String agentId();

    default Set<String> aliasIds() {
        return Set.of();
    }

    String displayName();

    Optional<P> decode(Game game, Player user, String payload);

    default String exhaustAnnouncement(AgentUse<P> use) {
        return use.user().getRepresentation() + " has exhausted " + use.agentName() + ".";
    }

    AgentOutcome resolve(AgentUse<P> use);
}
