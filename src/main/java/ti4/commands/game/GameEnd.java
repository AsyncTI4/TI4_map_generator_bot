package ti4.commands.game;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class GameEnd extends GameSubcommandData {
    public GameEnd() {
            super(Constants.GAME_END, "Declare the game has ended & informs @Bothelper");
            addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm ending the game with 'YES'").setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        MapManager mapManager = MapManager.getInstance();
        Map userActiveMap = mapManager.getUserActiveMap(event.getUser().getId());

        if (userActiveMap == null) {
            MessageHelper.replyToMessage(event, "Must set active Game");
            return;
        }
        String gameName = userActiveMap.getName();
        if (!event.getChannel().getName().startsWith(gameName + "-")) {
            MessageHelper.replyToMessage(event, "`/game end` must be executed in game channel only!");
            return;
        }
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with 'YES'");
            return;
        }

        List<Role> gameRoles = event.getGuild().getRolesByName(gameName, true);
        boolean deleteRole = true;
        if (gameRoles.size() > 1) {
            MessageHelper.replyToMessage(event, "There are multiple roles that match this game name (" + gameName + "): " + gameRoles);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please call a @Bothelper to fix this before using `/game end`");
            return;
        } else if (gameRoles.size() == 0) {
            MessageHelper.replyToMessage(event, "No roles match the game name (" + gameName + ") - no role will be deleted.");
            deleteRole = false;
        }
        //ADD USER PERMISSIONS DIRECTLY TO CHANNEL
        Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), getActiveMap());

        //DELETE THE ROLE
        if (deleteRole) {
            Role gameRole = gameRoles.get(0);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Role deleted: " + gameRole.getName());
            gameRole.delete().queue();
        }

        //POST GAME INFO
        userActiveMap.setHasEnded(true);
        MapSaveLoadManager.saveMap(userActiveMap);
        MessageHelper.sendMessageToChannel(event.getChannel(), Info.getGameInfo(null, null, userActiveMap, null).toString());

        //SEND THE MAP IMAGE
        File file = GenerateMap.getInstance().saveImage(userActiveMap, DisplayType.map, event);
        MessageHelper.replyToMessage(event, file);
        
        //ASK USERS FOR SUMMARY
        TextChannel pbdChroniclesChannel = event.getGuild().getTextChannelsByName("the-pbd-chronicles", true).get(0);
        String channelMention = pbdChroniclesChannel == null ? "#the-pbd-chronicles" : pbdChroniclesChannel.getAsMention();
        StringBuilder message = new StringBuilder();
        for (String playerID : userActiveMap.getPlayerIDs()) {
            Member member = event.getGuild().getMemberById(playerID);
            if (member != null) message.append(member.getAsMention());
        }

        message.append("\nPlease provide a summary of the game for the @Bothelper to post into " + channelMention);
        MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
        
        //INFORM BOTHELPER
        MessageHelper.sendMessageToChannel(event.getChannel(), event.getGuild().getRolesByName("Bothelper", true).get(0).getAsMention() + " - this game has concluded.");
        // TextChannel bothelperLoungeChannel = event.getGuild().getTextChannelById(1029569891193331712l);
        TextChannel bothelperLoungeChannel = event.getGuild().getTextChannelsByName("bothelper-lounge", true).get(0);
        if (bothelperLoungeChannel != null) MessageHelper.sendMessageToChannel(bothelperLoungeChannel, event.getChannel().getAsMention() + " - Game: " + gameName + " has concluded.\nReact here when a post has been made in " + channelMention);      
    
        //MOVE CHANNELS TO IN-LIMBO
        Category inLimboCategory = event.getGuild().getCategoriesByName("The in-limbo PBD Archive", true).get(0);
        TextChannel tableTalkChannel = (TextChannel) userActiveMap.getTableTalkChannel();
        if (inLimboCategory != null) {
            if (tableTalkChannel != null) {
                tableTalkChannel.getManager().setParent(inLimboCategory).queue();
                MessageHelper.sendMessageToChannel(tableTalkChannel, "Channel has been moved to Category: " + inLimboCategory.getName());
            }
            TextChannel actionsChannel = (TextChannel) userActiveMap.getMainGameChannel();
            if (actionsChannel != null) {
                actionsChannel.getManager().setParent(inLimboCategory).queue();
                MessageHelper.sendMessageToChannel(actionsChannel, "Channel has been moved to Category: " + inLimboCategory.getName());
            }
        }
    }

    public static String getGameEndText(Map map, SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**").append(map.getName()).append(" - ").append(map.getCustomName()).append("\n");
        sb.append(map.getCreationDate()).append(" - ").append(map.getLastModifiedDate());
        sb.append("\n");
        sb.append("Players: ").append("\n");
        HashMap<String, Player> players = map.getPlayers();
        int index = 1;
        for (Player player : players.values()) {
            if (player.getFaction() != null && !player.isDummy()) {
                sb.append("`").append(index).append(".` ").append(event.getJDA().getUserById(player.getUserID()).getAsMention()).append(Helper.getFactionIconFromDiscord(player.getFaction())).append("\n");
                index++;
            }
        }
        

        sb.append("Game Info:").append("\n");
        sb.append("Game name: " + map.getName()).append("\n");
        sb.append("Game owner: " + map.getOwnerName()).append("\n");
        sb.append("Game status: " + map.getMapStatus());
        if (map.isHasEnded()) sb.append(" - GAME HAS ENDED");
        sb.append("\n");
        sb.append("Game Modes: " + map.getGameModesText()).append("\n");
        sb.append("Created: " + map.getCreationDate()).append("\n");
        sb.append("Last Modified: " + Helper.getDateRepresentation(map.getLastModifiedDate())).append("\n");

        sb.append("Map String: `" + Helper.getMapString(map)).append("`").append("\n");

        sb.append("Game player count: " + map.getPlayerCountForMap()).append("\n");

            sb.append("Players: ").append("\n");
            HashMap<String, Player> players = map.getPlayers();
            int index = 1;
            ArrayList<Player> playerNames = new ArrayList<>(players.values());
            for (Player value : playerNames) {
                if (value.getFaction() != null) {
                    sb.append(index).append(". ").append(value.getUserName()).append(Helper.getFactionIconFromDiscord(value.getFaction())).append("\n");
                    index++;
                }
            }

        return sb.toString();
    }
}
