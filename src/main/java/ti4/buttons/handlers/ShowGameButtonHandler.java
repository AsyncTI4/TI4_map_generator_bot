package ti4.buttons.handlers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;

@UtilityClass
class ShowGameButtonHandler {

    @ButtonHandler("showGameAgain")
    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        ShowGameService.simpleShowGame(game, event);
    }

    @ButtonHandler("showMap")
    public static void showMap(Game game, ButtonInteractionEvent event) {
        MapRenderPipeline.render(game, event, DisplayType.map, fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }

    @ButtonHandler("showPlayerAreas")
    public static void showPlayArea(Game game, ButtonInteractionEvent event) {
        MapRenderPipeline.render(game, event, DisplayType.stats, fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }
}
