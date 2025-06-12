package ti4.service.option;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class GameOptionService {

    public static void offerGameOptionButtons(Game game, MessageChannel channel) {
        List<Button> factionReactButtons = new ArrayList<>();
        factionReactButtons.add(Buttons.green("enableAidReacts", "Enable Faction Reactions"));
        factionReactButtons.add(Buttons.red("disableAidReacts", "No Faction Reactions"));
        factionReactButtons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, "Enable to have the bot react to player messages with their faction emoji.", factionReactButtons);

        List<Button> hexBorderButtons = new ArrayList<>();
        hexBorderButtons.add(Buttons.green("showHexBorders_dash", "Dashed line"));
        hexBorderButtons.add(Buttons.blue("showHexBorders_solid", "Solid line"));
        hexBorderButtons.add(Buttons.red("showHexBorders_off", "Off (default)"));
        hexBorderButtons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, "Show borders around systems with player's ships.", hexBorderButtons);

        //sendShowOwnedPNsInPlayerAreaButton(game, channel);
        List<Button> daneLinkButtons = getDaneLeakModeButtons(game);
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, "Enable or Disable Galactic Events.", daneLinkButtons);
    }

    public static final Button showOwnedPNs_ON = Buttons.green("showOwnedPNsInPlayerArea_turnOFF", "ON");
    public static final Button showOwnedPNs_OFF = Buttons.red("showOwnedPNsInPlayerArea_turnON", "OFF");

    public static List<Button> getDaneLeakModeButtons(Game game) {
        List<Button> daneLinkButtons = new ArrayList<>();

        if (game.isMinorFactionsMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_minorFactions_disable", "Disable Minor Factions"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_minorFactions_enable", "Enable Minor Factions"));
        }
        if (game.isHiddenAgendaMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_hiddenAgenda_disable", "Disable Hidden Agenda"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_hiddenAgenda_enable", "Enable Hidden Agenda"));
        }
        if (game.isAgeOfExplorationMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_ageOfExploration_disable", "Disable Age of Exploration"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_ageOfExploration_enable", "Enable Age of Exploration"));
        }
        if (game.isTotalWarMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_totalWar_disable", "Disable Total War"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_totalWar_enable", "Enable Total War"));
        }
        if (game.isAgeOfCommerceMode()) {
            daneLinkButtons.add(Buttons.red("enableDaneMode_ageOfCommerce_disable", "Disable Age of Commerce"));
        } else {
            daneLinkButtons.add(Buttons.green("enableDaneMode_ageOfCommerce_enable", "Enable Age of Commerce"));
        }

        daneLinkButtons.add(Buttons.gray("deleteButtons", "Done"));

        return daneLinkButtons;
    }

    public static void sendShowOwnedPNsInPlayerAreaButton(Game game, MessageChannel channel) {
        List<Button> buttons = new ArrayList<>();
        if (game.isShowOwnedPNsInPlayerArea()) { //button shows current status
            buttons.add(showOwnedPNs_ON);
        } else {
            buttons.add(showOwnedPNs_OFF);
        }
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, "Show Owned PNs in Player Area?", buttons);
    }
}
