package ti4.buttons.handlers.options;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.option.GameOptionService;

class GameOptionButtonHandler {

    @ButtonHandler("enableAidReacts")
    public static void enableAidReact(ButtonInteractionEvent event, Game game) {
        game.setBotFactionReacts(true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Faction reaction icons have been enabled. Use `/game options` to change this.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("disableAidReacts")
    public static void disableAidReact(ButtonInteractionEvent event, Game game) {
        game.setBotFactionReacts(false);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Faction reaction icons have been disabled. Use `/game options` to change this.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("showHexBorders_")
    public static void editShowHexBorders(ButtonInteractionEvent event, Game game, String buttonID) {
        String value = buttonID.replace("showHexBorders_", "");
        game.setHexBorderStyle(value);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Updated Hex Border Style to `" + value + "`.\nUse `/game options` to change this.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("offerGameOptionButtons")
    public static void offerGameOptionButtons(Game game, MessageChannel channel) {
        GameOptionService.offerGameOptionButtons(game, channel);
    }

    @ButtonHandler("showOwnedPNsInPlayerArea_turnON")
    public static void showOwnedPNsInPlayerArea_turnON(ButtonInteractionEvent event, Game game) {
        game.setShowOwnedPNsInPlayerArea(true);
        event.editButton(GameOptionService.showOwnedPNs_ON).queue();
    }

    @ButtonHandler("showOwnedPNsInPlayerArea_turnOFF")
    public static void showOwnedPNsInPlayerArea_turnOFF(ButtonInteractionEvent event, Game game) {
        game.setShowOwnedPNsInPlayerArea(false);
        event.editButton(GameOptionService.showOwnedPNs_OFF).queue();
    }
}
