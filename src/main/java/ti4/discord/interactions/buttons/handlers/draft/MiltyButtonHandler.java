package ti4.discord.interactions.buttons.handlers.draft;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.service.milty.MiltyService;

@UtilityClass
class MiltyButtonHandler {

    @ButtonHandler("miltySetup")
    public static void miltySetup(GenericInteractionCreateEvent event, Game game) {
        MiltyService.miltySetup(event, game);
    }
}
