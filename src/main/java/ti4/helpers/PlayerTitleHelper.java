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

public class PlayerTitleHelper {

    public static void offerEveryoneTitlePossibilities(Game game) {
        for (Player player : game.getRealAndEliminatedPlayers()) {
            String msg = player.getRepresentation() + " you have the opportunity to anonymously bestow one title on someone else in this game. Titles are just for fun, and have no real significance, but could a nice way to take something away from this game. Feel free to not. If you choose to, it's a 2 button process. First select the title, then the player you want to bestow it upon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("bestowTitleStep1_Life Of The Table", "Life Of The Table"));
            buttons.add(Buttons.green("bestowTitleStep1_Fun To Be Around", "Fun To Be Around"));
            buttons.add(Buttons.green("bestowTitleStep1_Trustworthy To A Fault", "Trustworthy To A Fault"));
            buttons.add(Buttons.green("bestowTitleStep1_You Made The Game Better", "You Made The Game Better"));
            buttons.add(Buttons.green("bestowTitleStep1_A Kind Soul", "A Kind Soul"));
            buttons.add(Buttons.green("bestowTitleStep1_A Good Ally", "A Good Ally"));
            buttons.add(Buttons.green("bestowTitleStep1_A Mahact Puppet Master", "A Mahact Puppet Master"));

            buttons.add(Buttons.blue("bestowTitleStep1_Lightning Fast", "Lightning Fast"));
            buttons.add(Buttons.blue("bestowTitleStep1_Fortune Favored", "Fortune Favored"));
            buttons.add(Buttons.blue("bestowTitleStep1_Possesses Cursed Dice", "Possesses Cursed Dice"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Great Hollywooder", "A Great Hollywooder"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Worthy Opponent", "A Worthy Opponent"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Brilliant Tactician", "A Brilliant Tactician"));
            buttons.add(Buttons.blue("bestowTitleStep1_A Master Diplomat", "A Master Diplomat"));
            buttons.add(Buttons.blue("bestowTitleStep1_Hard To Kill", "Hard To Kill"));
            buttons.add(Buttons.blue("bestowTitleStep1_Shard Fumbler", "Shard Fumbler"));
            buttons.add(Buttons.gray("bestowTitleStep1_Observer", "Observer"));

            buttons.add(Buttons.red("bestowTitleStep1_A Sneaky One", "A Sneaky One"));
            buttons.add(Buttons.red("bestowTitleStep1_You Made Me Mad", "You Made Me Mad"));
            buttons.add(Buttons.red("bestowTitleStep1_A Vuil'Raith In Xxcha Clothing", "A Vuil'Raith In Xxcha Clothing"));
            buttons.add(Buttons.red("bestowTitleStep1_Space Risker", "Space Risker"));
            buttons.add(Buttons.red("bestowTitleStep1_A Warlord", "A Warlord"));
            buttons.add(Buttons.red("bestowTitleStep1_Traitor", "Traitor"));
            buttons.add(Buttons.red("bestowTitleStep1_Saltshaker", "Saltshaker"));

            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Tiles here", buttons);
        }
    }

    @ButtonHandler("bestowTitleStep1_")
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
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bestowTitleStep2_")
    public static void resolveBestowTitleStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
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
