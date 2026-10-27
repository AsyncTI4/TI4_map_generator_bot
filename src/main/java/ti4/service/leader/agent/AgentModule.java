package ti4.service.leader.agent;

import java.util.Optional;
import java.util.Set;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;

public abstract class AgentModule<P> {

    private static final String YSSARIL_AGENT = "yssarilagent";

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

    protected static String ownedButtonId(Player owner, String agentId, String... payloadSegments) {
        return owner.factionButtonChecker() + AgentButtonIds.format(agentId, payloadSegments);
    }

    protected static String useLabel(Player owner, String agentId, String agentLabel) {
        boolean holdsOnlyAYssarilCopy = !owner.hasLeader(agentId) && owner.hasLeader(YSSARIL_AGENT);
        return (holdsOnlyAYssarilCopy ? "Use Clever Clever " : "Use ") + agentLabel;
    }
}
