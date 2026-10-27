package ti4.service.leader.agent;

import java.util.function.Function;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.leader.agent.AgentOutcome.Message;

public record AgentUse<P>(
        Game game, Player user, P payload, String agentName, boolean viaYssaril, GenericInteractionCreateEvent event) {

    public AgentOutcome inActiveSystem(Function<Tile, AgentOutcome> resolution) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return AgentOutcome.of(Message.to(user, "Could not find the active system."));
        }
        return resolution.apply(activeSystem);
    }
}
