package ti4.buttons.handlers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.service.RematchService;

@UtilityClass
class RematchButtonHandler {

    @ButtonHandler("rematch")
    public static void rematch(Game game, GenericInteractionCreateEvent event) {
        RematchService.rematch(game, event);
    }
}
