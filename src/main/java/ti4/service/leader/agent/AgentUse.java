package ti4.service.leader.agent;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;

public record AgentUse<P>(Game game, Player user, P payload, String agentName, GenericInteractionCreateEvent event) {}
