package ti4.buttons.handlers.map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.function.Consumers;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.service.map.AddTileListService;

@UtilityClass
class AddTileListButtonHandler {

    @ButtonHandler("addMapString~MDL")
    public static void presentMapStringModal(ButtonInteractionEvent event, Game game) {
        Modal modal = AddTileListService.buildMapStringModal(game, "addMapString");
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
