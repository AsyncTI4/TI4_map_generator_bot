package ti4.buttons.handlers.planet;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.turn.StartTurnService;

@UtilityClass
class PrismButtonHandler {

    @ButtonHandler("newPrism@")
    public static void newPrismPart2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String techOut = buttonID.split("@")[1];
        player.purgeTech(techOut);
        TechnologyModel techM1 = Mapper.getTech(techOut);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " purged the technology _" + techM1.getName() + "_.");
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), player.getRepresentation()
            + ", use the button to get a technology that also has " + techM1.getRequirements().orElse("").length() + " prerequisites.", Buttons.GET_A_FREE_TECH);
        event.getMessage().delete().queue();
        String message2 = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, systemButtons);
    }

    
}
