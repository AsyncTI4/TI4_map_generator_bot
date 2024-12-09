package ti4.buttons.handlers.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.service.game.RematchService;

@UtilityClass
class RematchButtonHandler {

    @ButtonHandler("rematch")
    public static void rematch(Game game, GenericInteractionCreateEvent event) {
        AsyncTI4DiscordBot.runAsync("Rematch button task", () -> RematchService.rematch(game, event));
    }
}
