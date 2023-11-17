package ti4.commands.player;

import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.cardsac.PlayAC;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TurnStart extends PlayerSubcommandData {
    public TurnStart() {
        super(Constants.TURN_START, "Start Turn");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player mainPlayer = activeGame.getPlayer(getUser().getId());
        mainPlayer = Helper.getGamePlayer(activeGame, mainPlayer, event, null);
        mainPlayer = Helper.getPlayer(activeGame, mainPlayer, event);

        if (mainPlayer == null) {
            sendMessage("Player/Faction/Color could not be found in map:" + activeGame.getName());
            return;
        }
        turnStart(event, activeGame, mainPlayer);
    }

    public static void turnStart(GenericInteractionCreateEvent event, Game activeGame, Player player) {

        boolean goingToPass = false;
        if(activeGame.getFactionsThatReactedToThis("Pre Pass "+player.getFaction())!= null &&  activeGame.getFactionsThatReactedToThis("Pre Pass "+player.getFaction()).contains(player.getFaction())){
            Player p2 = player;
            if(activeGame.getFactionsThatReactedToThis("Pre Pass "+player.getFaction()).contains(p2.getFaction())&&!p2.isPassed()){
                activeGame.setCurrentReacts("Pre Pass "+player.getFaction(), "");
                goingToPass = true;
            }
        }
        String text = "# " + Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true) + " UP NEXT";
        String buttonText = "Use buttons to do your turn. ";
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeGame, false, event);
        MessageChannel gameChannel = activeGame.getMainGameChannel() == null ? event.getMessageChannel() : activeGame.getMainGameChannel();
        
        activeGame.updateActivePlayer(player);
        activeGame.setCurrentPhase("action");
        ButtonHelperFactionSpecific.resolveMilitarySupportCheck(player, activeGame);             
        ButtonHelperFactionSpecific.resolveKolleccAbilities(player, activeGame);
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(activeGame, event);
        if (isFowPrivateGame) {
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "started turn");

            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(player, activeGame, event, text, fail, success);
            if(!goingToPass){
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), buttonText, buttons);
            }
            if (getMissedSCFollowsText(activeGame, player) != null && !getMissedSCFollowsText(activeGame, player).equalsIgnoreCase("")) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), getMissedSCFollowsText(activeGame, player));
            }
            if (player.getStasisInfantry() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Use buttons to revive infantry. You have " + player.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, player));
            }

            activeGame.setPingSystemCounter(0);
            for (int x = 0; x < 10; x++) {
                activeGame.setTileAsPinged(x, null);
            }
        } else {
            MessageHelper.sendMessageToChannel(gameChannel, text);
            if(!goingToPass){
                MessageHelper.sendMessageToChannelWithButtons(gameChannel, buttonText, buttons);
            }
            if (getMissedSCFollowsText(activeGame, player) != null && !"".equalsIgnoreCase(getMissedSCFollowsText(activeGame, player))) {
                MessageHelper.sendMessageToChannel(gameChannel, getMissedSCFollowsText(activeGame, player));
            }
            if (player.getStasisInfantry() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Use buttons to revive infantry. You have " + player.getStasisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(activeGame, player));
            }
        }
        if(goingToPass){
            player.setPassed(true);
            String text2 = player.getRepresentation() + " PASSED";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), text2);
            TurnEnd.pingNextPlayer(event, activeGame, player);
        }
    }

    public static String getMissedSCFollowsText(Game activeGame, Player player) {
        if (!activeGame.isStratPings()) return null;
        boolean sendReminder = false;

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true));
        sb.append(" Please react to the following strategy cards before doing anything else:\n");
        int count = 0;
        for (int sc : activeGame.getPlayedSCsInOrder(player)) {
            if (!player.hasFollowedSC(sc)) {
                sb.append("> ").append(Helper.getSCRepresentation(activeGame, sc));
                if(!activeGame.getFactionsThatReactedToThis("scPlay"+sc).isEmpty()){
                    sb.append(" "+activeGame.getFactionsThatReactedToThis("scPlay"+sc));
                }
                sb.append("\n");
                sendReminder = true;
                count++;
            }
        }
        sb.append("You currently have ").append(player.getStrategicCC()).append(" CC in your strategy pool.");
        if (count > 1) {
            sb.append("\nMake sure to resolve the strategy cards in the order they were played.");
        }
        return sendReminder ? sb.toString() : null;
    }
}
