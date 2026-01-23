package ti4.service.option;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class GameOptionService {

    public static void offerGameOptionButtons(Game game, MessageChannel channel) {
        sendFactionReactOptionButtons(channel);
        sendHexBorderOptionButtons(channel);
        sendShowOwnedPNsInPlayerAreaButton(game, channel);
    }

    private static void sendFactionReactOptionButtons(MessageChannel channel) {
        List<Button> factionReactButtons = new ArrayList<>();
        factionReactButtons.add(Buttons.blue("enableAidReacts_faction", "Enable Faction Reactions"));
        factionReactButtons.add(Buttons.blue("enableAidReacts_colour", "Enable Colour Reactions"));
        factionReactButtons.add(Buttons.blue("enableAidReacts_strategy", "Enable Strategy Card Reactions"));
        factionReactButtons.add(Buttons.green("enableAidReacts_all", "Enable All Reactions"));
        factionReactButtons.add(Buttons.red("disableAidReacts", "No Reactions"));
        factionReactButtons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel,
                "Enable to have the bot react to player messages with their faction emoji, their colour emoji, their strategy card emoji, or all of the above.",
                factionReactButtons);
    }

    private static void sendHexBorderOptionButtons(MessageChannel channel) {
        List<Button> hexBorderButtons = new ArrayList<>();
        hexBorderButtons.add(Buttons.green("showHexBorders_dash", "Dashed line"));
        hexBorderButtons.add(Buttons.blue("showHexBorders_solid", "Solid line"));
        hexBorderButtons.add(Buttons.red("showHexBorders_off", "Off (default)"));
        hexBorderButtons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel, "Show borders around systems with player's ships.", hexBorderButtons);
    }

    public static final Button showOwnedPNs_ON = Buttons.green("showOwnedPNsInPlayerArea_turnOFF", "ON");
    public static final Button showOwnedPNs_OFF = Buttons.red("showOwnedPNsInPlayerArea_turnON", "OFF");

    private static void sendShowOwnedPNsInPlayerAreaButton(Game game, MessageChannel channel) {
        List<Button> buttons = new ArrayList<>();
        if (game.isShowOwnedPNsInPlayerArea()) { // button shows current status
            buttons.add(showOwnedPNs_ON);
        } else {
            buttons.add(showOwnedPNs_OFF);
        }
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                channel, "Show Owned Promissory Notes in Player Area?\n-# Click to toggle.", buttons);
    }
}
