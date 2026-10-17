package ti4.service.leader.agent;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;

public record AgentUse<P>(
        Game game,
        Player user,
        Leader exhaustedLeader,
        String agentId,
        String displayName,
        P payload,
        GenericInteractionCreateEvent event) {

    public boolean viaYssaril() {
        return AgentNames.isYssarilCopy(exhaustedLeader.getId(), agentId);
    }

    public String agentName() {
        return AgentNames.cardName(displayName, viaYssaril());
    }
}
