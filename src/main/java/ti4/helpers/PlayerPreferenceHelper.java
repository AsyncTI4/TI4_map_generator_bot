package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlayerPreferenceHelper {

    @ButtonHandler("offerPlayerPref")
    public static void offerPlayerPreferences(Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("playerPref_autoSaboReact", "Change Auto No-Sabo React Time", Emojis.ActionCard));
        buttons.add(Buttons.gray("playerPref_afkTimes", "Change AFK Times"));
        buttons.add(Buttons.gray("playerPref_tacticalAction", "Change Distance-Based Tactical Action Preference"));
        buttons.add(Buttons.gray("playerPref_autoNoWhensAfters", "Change Auto No Whens/Afters React", Emojis.Agenda));
        buttons.add(Buttons.OFFER_PING_OPTIONS_BUTTON);
        buttons.add(Buttons.gray("playerPref_directHitManagement", "Tell The Bot What Units Not To Risk Direct Hit On"));
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + " Choose the thing you wish to change", buttons);
    }

    @ButtonHandler("playerPref_")
    public static void resolvePlayerPref(Player player, ButtonInteractionEvent event, String buttonID, Game game) {
        String thing = buttonID.split("_")[1];
        switch (thing) {
            case "autoSaboReact" -> {
                offerSetAutoPassOnSaboButtons(game, player);
            }
            case "afkTimes" -> {
                offerAFKTimeOptions(game, player);
            }
            case "tacticalAction" -> {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " Choose whether you want your tactical action buttons to be distance based (offer you 0 tiles away initially, then 1, 2, 3 tiles away upon more button presses) or ring based (choose what ring the active system is in). Default is ring based. This will apply to all your games";
                buttons.add(Buttons.green("playerPrefDecision_true_distance", "Make it distance based"));
                buttons.add(Buttons.green("playerPrefDecision_false_distance", "Make it ring based"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }
            case "autoNoWhensAfters" -> {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " Choose whether you want the game to auto react no whens/afters after a random amount of time for you when you have no whens/afters. Default is off. This will only apply to this game. If you have any whens or afters or related when/after abilities, it will not do anything. ";
                buttons.add(Buttons.green("playerPrefDecision_true_agenda", "Turn on"));
                buttons.add(Buttons.green("playerPrefDecision_false_agenda", "Turn off"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }
            case "directHitManagement" -> {
                offerDirectHitManagementOptions(game, player);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("playerPrefDecision_")
    public static void resolvePlayerPrefDecision(Player player, ButtonInteractionEvent event, String buttonID,
        Game game) {
        String trueOrFalse = buttonID.split("_")[1];
        String distanceOrAgenda = buttonID.split("_")[2];
        if ("true".equals(trueOrFalse)) {
            if ("distance".equals(distanceOrAgenda)) {
                player.setPreferenceForDistanceBasedTacticalActions(true);
                Map<String, Game> mapList = GameManager.getGameNameToGame();
                for (Game game2 : mapList.values()) {
                    for (Player player2 : game2.getRealPlayers()) {
                        if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                            player2.setPreferenceForDistanceBasedTacticalActions(true);
                            GameSaveLoadManager.saveGame(game2, player2.getUserName() + " Updated Player Settings");
                        }
                    }
                }
            } else {
                player.setAutoPassWhensAfters(true);
            }
        } else {
            if ("distance".equals(distanceOrAgenda)) {
                player.setPreferenceForDistanceBasedTacticalActions(false);
                Map<String, Game> mapList = GameManager.getGameNameToGame();
                for (Game game2 : mapList.values()) {
                    for (Player player2 : game2.getRealPlayers()) {
                        if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                            player2.setPreferenceForDistanceBasedTacticalActions(false);
                            GameSaveLoadManager.saveGame(game2, player2.getUserName() + " Updated Player Settings");
                        }
                    }
                }
            } else {
                player.setAutoPassWhensAfters(false);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Set setting successfully");

        ButtonHelper.deleteMessage(event);
    }

    public static void offerSetAutoPassOnSaboButtons(Game game, Player player2) {
        List<Button> buttons = new ArrayList<>();
        int x = 1;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        x = 2;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        x = 4;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        x = 6;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        x = 8;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        x = 16;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        x = 24;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        x = 36;
        buttons.add(Buttons.gray("setAutoPassMedian_" + x, "" + x));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        x = 0;
        buttons.add(Buttons.red("setAutoPassMedian_" + x, "Turn off (if already on)"));
        if (player2 == null) {
            for (Player player : game.getRealPlayers()) {
                String message = player.getRepresentationUnfogged()
                    + " you may choose to automatically pass on Sabos after a random amount of time if you don't have a Sabo/Instinct Training/Watcher mechs."
                    + " How it works is you secretly set a median time (in hours) here, and then from now on when an AC is played, the bot will randomly react for you, 50% of the time being above that amount of time and 50% below."
                    + " It's random so people can't derive much information from it. You are free to decline, no-one will ever know either way, but if necessary you may change your time later with /player stats.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            }
        } else {
            String message = player2.getRepresentationUnfogged()
                + " you may choose to automatically pass on Sabos after a random amount of time if you don't have a Sabo/Instinct Training/Watcher mechs. "
                + " How it works is you secretly set a median time (in hours) here, and then from now on when an AC is played, the bot will randomly react for you, 50% of the time being above that amount of time and 50% below."
                + " It's random so people can't derive much information from it. You are free to decline, no-one will ever know either way, but if necessary you may change your time later with /player stats.";
            MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), message, buttons);
        }
    }

    public static void offerAFKTimeOptions(Game game, Player player) {
        List<Button> buttons = getSetAFKButtons(game);
        player.setHoursThatPlayerIsAFK("");
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged()
            + " your afk times (if any) have been reset. Use buttons to select the hours (note they are in UTC) in which you're afk. If you select 8 for example, you will be set as AFK from 8:00 UTC to 8:59 UTC in every game you are in.",
            buttons);
    }

    public static List<Button> getSetAFKButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 24; x++) {
            buttons.add(Buttons.gray("setHourAsAFK_" + x, "" + x));
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        return buttons;
    }

    public static void offerDirectHitManagementOptions(Game game, Player player) {
        List<Button> buttons = getDirectHitManagementButtons(game, player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged()
            + " select the units you would like to either risk or not risk Direct Hit. Upgraded dreadnoughts will automatically risk Direct Hits.  ",
            buttons);
    }

    public static List<Button> getDirectHitManagementButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String stuffNotToSustain = game
            .getStoredValue("stuffNotToSustainFor" + player.getFaction());
        if (stuffNotToSustain.isEmpty()) {
            game.setStoredValue("stuffNotToSustainFor" + player.getFaction(), "warsun");
            stuffNotToSustain = "warsun";
        }
        String unit = "warsun";
        if (stuffNotToSustain.contains(unit)) {
            buttons.add(Buttons.red("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
        } else {
            buttons.add(Buttons.green("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
        }
        unit = "flagship";
        if (stuffNotToSustain.contains(unit)) {
            buttons.add(Buttons.red("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
        } else {
            buttons.add(Buttons.green("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
        }
        unit = "dreadnought";
        if (stuffNotToSustain.contains(unit)) {
            buttons.add(Buttons.red("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
        } else {
            buttons.add(Buttons.green("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
        }
        unit = "cruiser";
        if (player.hasTech("se2")) {
            if (stuffNotToSustain.contains(unit)) {
                buttons.add(Buttons.red("riskDirectHit_" + unit + "_yes", "Risk " + StringUtils.capitalize(unit)));
            } else {
                buttons.add(
                    Buttons.green("riskDirectHit_" + unit + "_no", "Don't Risk " + StringUtils.capitalize(unit)));
            }
        }
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler("riskDirectHit_")
    public static void resolveRiskDirectHit(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String yesOrNo = buttonID.split("_")[2];
        String unit = buttonID.split("_")[1];
        String stuffNotToSustain = game.getStoredValue("stuffNotToSustainFor" + player.getFaction());
        if ("yes".equalsIgnoreCase(yesOrNo)) {
            stuffNotToSustain = stuffNotToSustain.replace(unit, "");
        } else {
            stuffNotToSustain = stuffNotToSustain + unit;
        }
        if (stuffNotToSustain.isEmpty()) {
            stuffNotToSustain = "none";
        }
        game.setStoredValue("stuffNotToSustainFor" + player.getFaction(), stuffNotToSustain);
        List<Button> systemButtons = getDirectHitManagementButtons(game, player);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    @ButtonHandler("setHourAsAFK_")
    public static void resolveSetAFKTime(Game gameOG, Player player, String buttonID, ButtonInteractionEvent event) {
        String time = buttonID.split("_")[1];
        player.addHourThatIsAFK(time);
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji() + " Set hour " + time + " as a time that you are afk");
        Map<String, Game> mapList = GameManager.getGameNameToGame();
        String afkTimes = player.getHoursThatPlayerIsAFK();
        for (Game game : mapList.values()) {
            if (!game.isHasEnded()) {
                for (Player player2 : game.getRealPlayers()) {
                    if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                        player2.setHoursThatPlayerIsAFK(afkTimes);
                        GameSaveLoadManager.saveGame(game, player2.getUserName() + " Updated Player Settings");
                    }
                }
            }
        }
    }
}
