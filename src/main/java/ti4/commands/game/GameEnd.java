package ti4.commands.game;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.MapGenerator;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class GameEnd extends GameSubcommandData {
    public GameEnd() {
            super(Constants.GAME_END, "Declare the game has ended & informs @Bothelper");
            addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm ending the game with 'YES'").setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        GameManager gameManager = GameManager.getInstance();
        Game userActiveGame = gameManager.getUserActiveGame(event.getUser().getId());

        if (userActiveGame == null) {
            MessageHelper.replyToMessage(event, "Must set active Game");
            return;
        }
        String gameName = userActiveGame.getName();
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
        Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), getActiveGame());
        MessageHelper.sendMessageToChannel(event.getChannel(), "This game's channels' permissions have been updated.");

        //DELETE THE ROLE
        if (deleteRole) {
            Role gameRole = gameRoles.get(0);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Role deleted: " + gameRole.getName() + " - use `/game ping` to ping all players");
            gameRole.delete().queue();
        }

        //POST GAME INFO
        MessageHelper.sendMessageToChannel(event.getChannel(), "**Game: `" + gameName + "` has ended!**");
        userActiveGame.setHasEnded(true);
        userActiveGame.setEndedDate(new Date().getTime());
        GameSaveLoadManager.saveMap(userActiveGame, event);
        String gameEndText = getGameEndText(userActiveGame, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), gameEndText);
        userActiveGame.setAutoPing(false);
        userActiveGame.setAutoPingSpacer(0);
        //SEND THE MAP IMAGE
        File file = GenerateMap.getInstance().saveImage(userActiveGame, DisplayType.all, event);
        MessageHelper.replyToMessage(event, file);

        //CREATE POST IN #THE-PBD-CHRONICLES
        
        TextChannel pbdChroniclesChannel = MapGenerator.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true).get(0);
        String channelMention = pbdChroniclesChannel == null ? "#the-pbd-chronicles" : pbdChroniclesChannel.getAsMention();
        if (pbdChroniclesChannel == null) {
            BotLogger.log(event, "`#the-pbd-chronicles` channel not found - `/game end` cannot post summary");
            return;
        }
        StringBuilder message = new StringBuilder();
        for (String playerID : userActiveGame.getPlayerIDs()) { //GET ALL PLAYER PINGS
            Member member = event.getGuild().getMemberById(playerID);
            if (member != null) message.append(member.getAsMention());
        }
        message.append("\nPlease provide a summary of the game below:");
        String bothelperMention = Helper.getEventGuildRole(event, "bothelper").getAsMention();

        if(!userActiveGame.isFoWMode())
        {
            //INFORM PLAYERS
            pbdChroniclesChannel.sendMessage(gameEndText).queue(m -> { //POST INITIAL MESSAGE
                try (FileUpload fileUpload = FileUpload.fromData(file)) {
                    m.editMessageAttachments(fileUpload).queue(); //ADD MAP FILE TO MESSAGE
                } catch (IOException e) {
                    BotLogger.log(event, "Error from fileUpload: " + e.getMessage());
                }
                m.createThreadChannel(gameName).queue(t -> t.sendMessage(message.toString()).queue()); //CREATE THREAD AND POST FOLLOW UP
                String msg = "Game summary has been posted in the " + channelMention + " channel. Please post a summary of the game there!";
                MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            });
        }
        TextChannel bothelperLoungeChannel = MapGenerator.guildPrimary.getTextChannelsByName("bothelper-lounge", true).get(0);
        //if (bothelperLoungeChannel != null) MessageHelper.sendMessageToChannel(bothelperLoungeChannel, "Game: **" + gameName + "** on server **" + event.getGuild().getName() + "** has concluded.");
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels == null){
             return;
        }
        String threadName = "game-starts-and-ends";
        // SEARCH FOR EXISTING OPEN THREAD
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                MessageHelper.sendMessageToChannel(threadChannel_,
                        "Game: **" + gameName + "** on server **" + event.getGuild().getName() + "** has concluded.");
            }
        }

        //MOVE CHANNELS TO IN-LIMBO
        Category inLimboCategory = event.getGuild().getCategoriesByName("The in-limbo PBD Archive", true).get(0);
        TextChannel tableTalkChannel = userActiveGame.getTableTalkChannel();
        TextChannel actionsChannel = userActiveGame.getMainGameChannel();
        if (inLimboCategory != null) {
            if (inLimboCategory.getChannels().size() > 40) {
                String holytispoonMention = event.getJDA().getUserById("150809002974904321").getAsMention();
                MessageHelper.sendMessageToChannel(bothelperLoungeChannel, inLimboCategory.getName() + " category on server " + inLimboCategory.getGuild().getName() + " is almost full. " + holytispoonMention + " - please make room soon!");
            }
            if (inLimboCategory.getChannels().size() > 48) { //HANDLE FULL IN-LIMBO
                MessageHelper.sendMessageToChannel(event.getChannel(), inLimboCategory.getName() + " Category is full. " + bothelperMention + " - please make room and manually move these channels.");
            } else {
                String moveMessage = "Channel has been moved to Category **" + inLimboCategory.getName() + "** and will be automatically cleaned up shortly.";
                if (tableTalkChannel != null) { //MOVE TABLETALK CHANNEL
                    tableTalkChannel.getManager().setParent(inLimboCategory).queue();
                    MessageHelper.sendMessageToChannel(tableTalkChannel, moveMessage);
                }
                if (actionsChannel != null) { //MOVE ACTIONS CHANNEL
                    actionsChannel.getManager().setParent(inLimboCategory).queue();
                    MessageHelper.sendMessageToChannel(actionsChannel, moveMessage);
                }
            }
        }

        //CLOSE THREADS IN CHANNELS
        if (tableTalkChannel != null) {
            for (ThreadChannel threadChannel : tableTalkChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
        }
        if (actionsChannel != null) {
            for (ThreadChannel threadChannel : actionsChannel.getThreadChannels()) {
                threadChannel.getManager().setArchived(true).queue();
            }
        }

        //DOWNLOAD CHANNEL BACKUP VIA CLI

        //POST FILE TO BACKUP CHANNEL

        //DELETE CHANNELS
    }

    public static String getGameEndText(Game activeGame, SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**").append(activeGame.getName()).append("**__ - ").append(activeGame.getCustomName()).append("\n");
        sb.append(activeGame.getCreationDate()).append(" - ").append(Helper.getDateRepresentation(activeGame.getLastModifiedDate()));
        sb.append("\n");
        sb.append("\n");
        sb.append("**Players:**").append("\n");
        HashMap<String, Player> players = activeGame.getPlayers();
        int index = 1;
        for (Player player : players.values()) {
            if (player.getFaction() == null || player.isDummy()) continue;
            
            int playerVP = player.getTotalVictoryPoints(activeGame);
            sb.append("> `").append(index).append(".` ");
            sb.append(Helper.getFactionIconFromDiscord(player.getFaction()));
            sb.append(Helper.getColourAsMention(MapGenerator.guildPrimary, player.getColor()));
            sb.append(event.getJDA().getUserById(player.getUserID()).getAsMention());
            sb.append(" - *").append(playerVP).append("VP* ");
            if (playerVP >= activeGame.getVp()) sb.append(" - **WINNER**");
            sb.append("\n");
            index++;
        }

        sb.append("\n");
        String gameModesText = activeGame.getGameModesText();
        if (gameModesText.isEmpty()) gameModesText = "None";
        sb.append("Game Modes: ").append(gameModesText).append("\n");

        return sb.toString();
    }
}
