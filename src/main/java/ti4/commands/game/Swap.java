package ti4.commands.game;

import java.util.Map;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class Swap extends GameSubcommandData {

    public Swap() {
        super(Constants.SWAP, "Swap factions with a player ");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Swap with player in Faction/Color ").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Replacement player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();
        Game activeGame = getActiveGame();
        Collection<Player> players = activeGame.getPlayers().values();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.adminRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        if (players.stream().noneMatch(player -> player.getUserID().equals(callerUser.getId())) && !isAdmin) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only game players can swap with a player.");
            return;
        }
        String message = "";
        OptionMapping removeOption = event.getOption(Constants.FACTION_COLOR);
        OptionMapping addOption = event.getOption(Constants.PLAYER2);
        if (removeOption != null && addOption != null) {
            Player removedPlayer = Helper.getPlayer(activeGame, null, event);
            Player swapperPlayer = activeGame.getPlayer(addOption.getAsUser().getId());
            if (removedPlayer == null){
                MessageHelper.replyToMessage(event, "Could not find player for faction/color to replace");
                return;
            }
            if (swapperPlayer == null || swapperPlayer.getFaction() == null){
                MessageHelper.replyToMessage(event, "Could not find faction/player to swap");
                return;
            }
            User addedUser = addOption.getAsUser();
            secondHalfOfSwap(activeGame, swapperPlayer, removedPlayer, addedUser, event);
        } else {
            MessageHelper.replyToMessage(event, "Specify player to swap");
            return;
        }
        GameSaveLoadManager.saveMap(activeGame, event);
        GameSaveLoadManager.reload(activeGame);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }
     public void secondHalfOfSwap(Game activeGame, Player swapperPlayer, Player removedPlayer, User addedUser, GenericInteractionCreateEvent event) {
        String message;
        Collection<Player> players = activeGame.getPlayers().values();
            if (players.stream().anyMatch(player -> player.getUserID().equals(removedPlayer.getUserID()))) {
                 message = "User controlling faction: " + removedPlayer.getFaction() + " swapped with player controlling: " + swapperPlayer.getFaction();
                Player player = activeGame.getPlayer(removedPlayer.getUserID());
                LinkedHashMap<String, List<String>> scoredPublicObjectives = activeGame.getScoredPublicObjectives();
                for (Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
                    List<String> value = poEntry.getValue();
                    boolean removed = value.remove(removedPlayer.getUserID());
                    boolean removed2 = value.remove(swapperPlayer.getUserID());
                    if (removed){
                        value.add(addedUser.getId());
                    }
                    if (removed2){
                        value.add(removedPlayer.getUserID());
                    }
                }
                if(player.isDummy()){
                    player.setDummy(false);
                    swapperPlayer.setDummy(true);
                }
                LinkedHashSet<Integer> holder = new LinkedHashSet<>(player.getSCs());
                player.setSCs(swapperPlayer.getSCs());
                swapperPlayer.setSCs(holder);
                swapperPlayer.setUserName(removedPlayer.getUserName());
                swapperPlayer.setUserID(removedPlayer.getUserID());
                player.setUserName(addedUser.getName());
                player.setUserID(addedUser.getId());
                
                if(activeGame.getActivePlayer() != null && activeGame.getActivePlayer().equalsIgnoreCase(player.getUserID())){
                    if(!activeGame.isFoWMode())
                    {
                        try {
                            if (activeGame.getLatestTransactionMsg() != null && !"".equals(activeGame.getLatestTransactionMsg())) {
                                activeGame.getMainGameChannel().deleteMessageById(activeGame.getLatestTransactionMsg()).queue();
                                activeGame.setLatestTransactionMsg("");
                            }
                        }
                        catch(Exception e) {
                            //  Block of code to handle errors
                        }
                    }
                    String text = "# " + Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true) + " UP NEXT";
                    String buttonText = "Use buttons to do your turn. ";
                    List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), text);
                    MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), buttonText, buttons);
                }
            } else {
                MessageHelper.replyToMessage(event, "Specify player that is in game to be swapped");
                return;
            }
        GameSaveLoadManager.saveMap(activeGame, event);
        GameSaveLoadManager.reload(activeGame);
       // SOInfo.sendSecretObjectiveInfo(activeMap, swapperPlayer);
       // SOInfo.sendSecretObjectiveInfo(activeMap, removedPlayer);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }
}