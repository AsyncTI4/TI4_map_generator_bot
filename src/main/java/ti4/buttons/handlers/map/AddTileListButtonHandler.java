package ti4.buttons.handlers.map;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.service.map.AddTileListService;

class AddTileListButtonHandler {

    @ButtonHandler("addMapString~MDL")
    public static void presentMapStringModal(ButtonInteractionEvent event, Game game) {
        Modal modal = AddTileListService.buildMapStringModal(game, "addMapString");
        event.replyModal(modal).queue();
    }
}
