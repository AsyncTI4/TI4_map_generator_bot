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
        String agentName,
        P payload,
        GenericInteractionCreateEvent event) {

    public static <P> AgentUse<P> of(
            AgentModule<P> module,
            Game game,
            Player user,
            Leader exhaustedLeader,
            String agentId,
            P payload,
            GenericInteractionCreateEvent event) {
        String agentName = module.cardName(AgentNames.isYssarilCopy(exhaustedLeader.getId(), agentId));
        return new AgentUse<>(game, user, exhaustedLeader, agentId, agentName, payload, event);
    }

    public boolean viaYssaril() {
        return AgentNames.isYssarilCopy(exhaustedLeader.getId(), agentId);
    }
}
