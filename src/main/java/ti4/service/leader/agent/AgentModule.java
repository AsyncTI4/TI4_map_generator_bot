package ti4.service.leader.agent;

import java.util.Optional;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;

public abstract class AgentModule<P> {

    private final String agentId;
    private final String displayName;

    protected AgentModule(String agentId, String displayName) {
        this.agentId = agentId;
        this.displayName = displayName;
    }

    public final String agentId() {
        return agentId;
    }

    public Set<String> aliasIds() {
        return Set.of();
    }

    public final String displayName() {
        return displayName;
    }

    public abstract Optional<P> decode(Game game, Player user, String payload);

    public String exhaustAnnouncement(AgentUse<P> use) {
        return use.user().getRepresentation() + " has exhausted " + use.agentName() + ".";
    }

    public abstract AgentOutcome resolve(AgentUse<P> use);
}
