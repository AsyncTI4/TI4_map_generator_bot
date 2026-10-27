package ti4.service.leader.agent;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;

public record AgentUse<P>(
        Game game, Player user, P payload, String agentName, boolean viaYssaril, GenericInteractionCreateEvent event) {

    static final String FOG_SAFE_AGENT_NAME = "an agent";

    public String agentNameFor(Player reader) {
        return game.isFowMode() && reader != user ? FOG_SAFE_AGENT_NAME : agentName;
    }

    public AgentOutcome.Message noteToUser(Player about, String textAfterName) {
        if (about == user) {
            return AgentOutcome.Message.addressedTo(user, textAfterName);
        }
        return AgentOutcome.Message.plain(user, about.getFactionEmojiOrColor() + textAfterName);
    }
}
