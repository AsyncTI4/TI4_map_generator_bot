package ti4.helpers;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticOptIn;
import ti4.settings.users.UserSettingsManager;
import ti4.website.WebHelper;

public class PlayerTitleHelper {

    public static void offerEveryoneTitlePossibilities(Game game) {
        for (Player player : game.getRealAndEliminatedPlayers()) {
            String msg = player.getRepresentation() + " you have the opportunity to anonymously bestow one title on someone else in this game."
                + " Titles are just for fun, and have no real significance, but could a nice way to take something away from this game."
                + " Feel free to not. If you choose to, it's a 2 button process. First select the title, then the player you wish to bestow it upon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("bestowTitleStep1_Life Of The Table", "Life Of The Table"));
            buttons.add(Buttons.green("bestowTitleStep1_Fun To Be Around", "Fun To Be Around"));
            buttons.add(Buttons.green("bestowTitleStep1_Trustworthy To A Fault", "Trustworthy To A Fault"));
            buttons.add(Buttons.green("bestowTitleStep1_You Made The Game Better", "You Made The Game Better"));
            buttons.add(Buttons.green("bestowTitleStep1_A Kind Soul", "A Kind Soul"));
            buttons.add(Buttons.green("bestowTitleStep1_A Good Ally", "A Good Ally"));
            buttons.add(Buttons.green("bestowTitleStep1_A Mahact Puppet Master", "A Mahact Puppet Master"));
            buttons.add(Buttons.green("bestowTitleStep1_Intergalactic Bard", "Intergalactic Bard"));

            // buttons.add(Buttons.blue("bestowTitleStep1_Lightning Fast", "Lightning Fast"));
            buttons.add(Buttons.blue("bestowTitleStep1_Fortune Favored", "Fortune Favored"));
            buttons.add(Buttons.blue("bestowTitleStep1_Possesses Cursed Dice", "Possesses Cursed Dice"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Great Hollywooder", "A Great Hollywooder"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Worthy Opponent", "A Worthy Opponent"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Brilliant Tactician", "A Brilliant Tactician"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Master Diplomat", "A Master Diplomat"));
            buttons.add(Buttons.blue("bestowTitleStep1_Hard To Kill", "Hard To Kill"));
            buttons.add(Buttons.blue("bestowTitleStep1_Shard Fumbler", "Shard Fumbler"));
            buttons.add(Buttons.blue("bestowTitleStep1_Rules Master", "Rules Master"));
            buttons.add(Buttons.gray("bestowTitleStep1_Observer", "Observer"));

            buttons.add(Buttons.red("bestowTitleStep1_A Sneaky One", "A Sneaky One"));
            buttons.add(Buttons.red("bestowTitleStep1_You Made Me Mad", "You Made Me Mad"));
            buttons.add(Buttons.red("bestowTitleStep1_A Vuil'Raith In Xxcha Clothing", "A Vuil'Raith In Xxcha Clothing"));
            //buttons.add(Buttons.red("bestowTitleStep1_Space Risker", "Space Risker"));
            buttons.add(Buttons.red("bestowTitleStep1_A Warlord", "A Warlord"));
            buttons.add(Buttons.red("bestowTitleStep1_Word Breaker", "Word Breaker"));
            buttons.add(Buttons.red("bestowTitleStep1_One To Be Feared", "One To Be Feared"));
            buttons.add(Buttons.red("bestowTitleStep1_Spice Bringer", "Spice Bringer"));

            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Titles here (if you dont see them, try exiting discord and reopening it)", buttons);
            var userSettings = UserSettingsManager.get(player.getUserID());
            if (!userSettings.isHasIndicatedStatPreferences()) {
                buttons = getOptInButtons(game, player);
                msg = player.getRepresentation() + " congratz on finishing a game of async! Async has a website that collects and displays games and player stats, but we don't want to display your game stats without your permission."
                    + " You can indicate what you're comfortable with displaying below with the buttons, and once you submit an answer you will not be asked again. However, you can always change"
                    + " your preferences with the /statisticis opt_in and /statistics opt_out commands.\nIf you want to see what a fully displayed profile looks like, check out this profile: https://www.ti4ultimate.com/community/async/player-profile?playerId=406";
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Submit decision with these buttons", buttons);
            } else {
                msg = "If you have any interest in general or specific player stats in async, feel free to check out this website: https://www.ti4ultimate.com/community/async/";
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            }

        }
    }

    public static List<Button> getOptInButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String key = player.getFaction() + "optin" + "winrate";
        if (game.getStoredValue(key).isEmpty() || game.getStoredValue(key).equalsIgnoreCase("no")) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Win Rate"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Win Rate"));
        }

        key = player.getFaction() + "optin" + "vps";
        if (game.getStoredValue(key).isEmpty() || game.getStoredValue(key).equalsIgnoreCase("no")) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Victory Points"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Victory Points"));
        }

        key = player.getFaction() + "optin" + "turns";
        if (game.getStoredValue(key).isEmpty() || game.getStoredValue(key).equalsIgnoreCase("no")) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Turn Time Stats"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Turn Time Stats"));
        }

        key = player.getFaction() + "optin" + "combats";
        if (game.getStoredValue(key).isEmpty() || game.getStoredValue(key).equalsIgnoreCase("no")) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Combat Data"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Combat Data"));
        }

        key = player.getFaction() + "optin" + "opponents";
        if (game.getStoredValue(key).isEmpty() || game.getStoredValue(key).equalsIgnoreCase("no")) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Opponents"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Opponents"));
        }

        key = player.getFaction() + "optin" + "factions";
        if (game.getStoredValue(key).isEmpty() || game.getStoredValue(key).equalsIgnoreCase("no")) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Factions Played"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Factions Played"));
        }

        key = player.getFaction() + "optin" + "games";
        if (game.getStoredValue(key).isEmpty() || game.getStoredValue(key).equalsIgnoreCase("no")) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Games"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Games"));
        }

        buttons.add(Buttons.blue("setOptInStats_acceptAll", "Click To Display All Stats"));
        buttons.add(Buttons.red("setOptInStats_declineAll", "Click To Display No Stats"));
        buttons.add(Buttons.gray("setOptInStats_some", "Click To Submit Decisions"));
        return buttons;
    }

    @ButtonHandler(value = "setOptInSinglePreference_")
    public static void setOptInSinglePreference(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue(buttonID.split("_")[1], buttonID.split("_")[2]);
        String msg = "Altered a value";
        event.getMessage().editMessage(msg).setComponents(ButtonHelper.turnButtonListIntoActionRowList(getOptInButtons(game, player))).queue();
    }

    @ButtonHandler(value = "setOptInStats_")
    public static void setOptInStats(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        var userSettings = UserSettingsManager.get(player.getUserID());
        userSettings.setHasIndicatedStatPreferences(true);
        UserSettingsManager.save(userSettings);
        String decision = buttonID.split("_")[1];
        var statisticsOpIn = new StatisticOptIn();
        statisticsOpIn.setPlayerDiscordId(event.getUser().getId());
        if (decision.equalsIgnoreCase("acceptAll")) {
            statisticsOpIn.setShowWinRates(true);
            statisticsOpIn.setShowTurnStats(true);
            statisticsOpIn.setShowCombatStats(true);
            statisticsOpIn.setShowVpStats(true);
            statisticsOpIn.setShowFactionStats(true);
            statisticsOpIn.setShowOpponents(true);
            statisticsOpIn.setShowGames(true);

        }
        if (decision.equalsIgnoreCase("declineAll")) {
            statisticsOpIn.setShowWinRates(false);
            statisticsOpIn.setShowTurnStats(false);
            statisticsOpIn.setShowCombatStats(false);
            statisticsOpIn.setShowVpStats(false);
            statisticsOpIn.setShowFactionStats(false);
            statisticsOpIn.setShowOpponents(false);
            statisticsOpIn.setShowGames(false);
        }
        if (decision.equalsIgnoreCase("some")) {
            String key = player.getFaction() + "optin" + "winrate";
            statisticsOpIn.setShowWinRates(game.getStoredValue(key).equalsIgnoreCase("yes"));
            key = player.getFaction() + "optin" + "turns";
            statisticsOpIn.setShowTurnStats(game.getStoredValue(key).equalsIgnoreCase("yes"));
            key = player.getFaction() + "optin" + "combats";
            statisticsOpIn.setShowCombatStats(game.getStoredValue(key).equalsIgnoreCase("yes"));
            key = player.getFaction() + "optin" + "vps";
            statisticsOpIn.setShowVpStats(game.getStoredValue(key).equalsIgnoreCase("yes"));
            key = player.getFaction() + "optin" + "factions";
            statisticsOpIn.setShowFactionStats(game.getStoredValue(key).equalsIgnoreCase("yes"));
            key = player.getFaction() + "optin" + "opponents";
            statisticsOpIn.setShowOpponents(game.getStoredValue(key).equalsIgnoreCase("yes"));
            key = player.getFaction() + "optin" + "games";
            statisticsOpIn.setShowGames(game.getStoredValue(key).equalsIgnoreCase("yes"));
        }
        WebHelper.sendStatisticsOptIn(statisticsOpIn);
        String msg = "Successfully logged your decision. Feel free to check out stats at https://www.ti4ultimate.com/community/async/";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
        ButtonHelper.deleteMessage(event);

    }

    @ButtonHandler(value = "bestowTitleStep1_", save = false)
    public static void resolveBestowTitleStep1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String title = buttonID.split("_")[1];
        String msg = player.getRepresentation() + " choose the player you wish to give the title of " + title;
        List<Button> buttons = new ArrayList<>();
        for (Player player2 : game.getRealPlayersNDummies()) {
            if (player2 == player) {
                continue;
            }
            buttons.add(Buttons.green("bestowTitleStep2_" + title + "_" + player2.getFaction(), player2.getFactionModel().getFactionName() + " (" + player2.getUserName() + ")"));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "bestowTitleStep2_")
    public static void resolveBestowTitleStep2(
        Game game, Player player, ButtonInteractionEvent event,
        String buttonID
    ) {
        String title = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String msg = p2.getRepresentation() + " someone has chosen to give you the title of '" + title + "'";
        String titles = game.getStoredValue("TitlesFor" + p2.getUserID());
        if (titles.isEmpty()) {
            game.setStoredValue("TitlesFor" + p2.getUserID(), title);
        } else {
            game.setStoredValue("TitlesFor" + p2.getUserID(), titles + "_" + title);
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Successfully bestowed title");
        MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), msg);
        ButtonHelper.deleteMessage(event);
    }
}
