package ti4.discord.interactions.routing;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;

@FunctionalInterface
public interface HandlerGuard {

    boolean allows(GenericInteractionCreateEvent event, Game game);
}
