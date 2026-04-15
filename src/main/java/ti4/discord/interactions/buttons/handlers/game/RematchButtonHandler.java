package ti4.discord.interactions.buttons.handlers.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.service.game.RematchService;

@UtilityClass
class RematchButtonHandler {

    @ButtonHandler("rematch")
    public static void rematch(Game game, GenericInteractionCreateEvent event) {
        RematchService.rematch(game, event);
    }
}
