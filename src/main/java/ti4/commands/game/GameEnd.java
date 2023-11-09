package ti4.commands.game;

import java.util.Date;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
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
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.PUBLISH, "True to publish results to #pbd-chronicles. (Default: True)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ARCHIVE_CHANNELS, "True to archive the channels and delete the game role (Default: True)"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        GameManager gameManager = GameManager.getInstance();
        Game activeGame = gameManager.getUserActiveGame(event.getUser().getId());

        if (activeGame == null) {
            MessageHelper.replyToMessage(event, "Must set active Game");
            return;
        }
        String gameName = activeGame.getName();
        if (!event.getChannel().getName().startsWith(gameName + "-")) {
            MessageHelper.replyToMessage(event, "`/game end` must be executed in game channel only!");
            return;
        }
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with 'YES'");
            return;
        }
        boolean publish = event.getOption(Constants.PUBLISH, true, OptionMapping::getAsBoolean);
        boolean archiveChannels = event.getOption(Constants.ARCHIVE_CHANNELS, true, OptionMapping::getAsBoolean);
        secondHalfOfGameEnd(event, activeGame, publish, archiveChannels);
    }

    public static void secondHalfOfGameEnd(GenericInteractionCreateEvent event, Game activeGame, boolean publish, boolean archiveChannels) {
        String gameName = activeGame.getName();
        List<Role> gameRoles = event.getGuild().getRolesByName(gameName, true);
        boolean deleteRole = true;
        if (gameRoles.size() > 1) {
            MessageHelper.replyToMessage(event, "There are multiple roles that match this game name (" + gameName + "): " + gameRoles);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please call a @Bothelper to fix this before using `/game end`");
            return;
        } else if (gameRoles.size() == 0) {
            MessageHelper.replyToMessage(event, "No roles match the game name (" + gameName + ") - no role will be deleted.");
            deleteRole = false;
        }

        //ADD USER PERMISSIONS DIRECTLY TO CHANNEL
        Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game's channels' permissions have been updated.");

        //DELETE THE ROLE
        if (deleteRole && archiveChannels) {
            Role gameRole = gameRoles.get(0);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Role deleted: " + gameRole.getName() + " - use `/game ping` to ping all players");
            gameRole.delete().queue();
        }

        //POST GAME INFO
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**Game: `" + gameName + "` has ended!**");
        activeGame.setHasEnded(true);
        activeGame.setEndedDate(new Date().getTime());
        GameSaveLoadManager.saveMap(activeGame, event);
        String gameEndText = getGameEndText(activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), gameEndText);
        activeGame.setAutoPing(false);
        activeGame.setAutoPingSpacer(0);

        //SEND THE MAP IMAGE
        FileUpload fileUpload = GenerateMap.getInstance().saveImage(activeGame, DisplayType.all, event);
        MessageHelper.replyToMessage(event, fileUpload);

        
        
        StringBuilder message = new StringBuilder();
        for (String playerID : activeGame.getRealPlayerIDs()) { //GET ALL PLAYER PINGS
            Member member = event.getGuild().getMemberById(playerID);
            if (member != null) message.append(member.getAsMention());
        }
        message.append("\nPlease provide a summary of the game below:");
        String bothelperMention = Helper.getRoleMentionByName(AsyncTI4DiscordBot.guildPrimary, "bothelper");

        Helper.checkThreadLimitAndArchive(AsyncTI4DiscordBot.guildPrimary);
        //CREATE POST IN #THE-PBD-CHRONICLES
        if (publish && AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true).size() > 0) {
            TextChannel pbdChroniclesChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("the-pbd-chronicles", true).get(0);
            String channelMention = pbdChroniclesChannel == null ? "#the-pbd-chronicles" : pbdChroniclesChannel.getAsMention();
            if (pbdChroniclesChannel == null) {
                BotLogger.log(event, "`#the-pbd-chronicles` channel not found - `/game end` cannot post summary");
                return;
            }
            if (!activeGame.isFoWMode()) {
                //INFORM PLAYERS
                pbdChroniclesChannel.sendMessage(gameEndText).queue(m -> { //POST INITIAL MESSAGE
                    m.editMessageAttachments(fileUpload).queueAfter(50, TimeUnit.MILLISECONDS); //ADD MAP FILE TO MESSAGE
                    m.createThreadChannel(gameName).queueAfter(500, TimeUnit.MILLISECONDS, t -> t.sendMessage(message.toString()).queue(null, (error) -> BotLogger.log("Failure to create Game End thread for **" + activeGame.getName() + "** in PBD Chronicles:\n> " + error.getMessage()))); //CREATE THREAD AND POST FOLLOW UP
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game summary has been posted in the " + channelMention + " channel: " + m.getJumpUrl());
                });
            }
        }

        // TIGL Extras
        if (activeGame.isCompetitiveTIGLGame() && activeGame.getGameWinner().isPresent()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), getTIGLFormattedGameEndText(activeGame, event));
        }

        // GET BOTHELPER LOUNGE
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("bothelper-lounge", true).get(0);
        if (bothelperLoungeChannel == null) {
            BotLogger.log(event, "`#bothelper-lounge` channel not found - `/game end` cannot continue");
            return;
        }

        //MOVE CHANNELS TO IN-LIMBO
        Category inLimboCategory = event.getGuild().getCategoriesByName("The in-limbo PBD Archive", true).get(0);
        TextChannel tableTalkChannel = activeGame.getTableTalkChannel();
        TextChannel actionsChannel = activeGame.getMainGameChannel();
        if (inLimboCategory != null && archiveChannels) {
            int maxLimboChannels = 40;
            int channelCountToDelete = maxLimboChannels / 2;
            if (inLimboCategory.getChannels().size() >= maxLimboChannels) {
                inLimboCategory.getChannels().stream().limit(channelCountToDelete).forEach(channel -> channel.delete().queue());
                MessageHelper.sendMessageToChannel(bothelperLoungeChannel,
                    inLimboCategory.getName() + " category on server " + inLimboCategory.getGuild().getName() + " had " + maxLimboChannels +" channels and " + channelCountToDelete + " were auto-cleaned");
            }
            if (inLimboCategory.getChannels().size() > 48) { //HANDLE FULL IN-LIMBO
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    inLimboCategory.getName() + " Category is full. " + bothelperMention + " - please make room and manually move these channels.");
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

        // POST GAME END TO BOTHELPER LOUNGE GAME STARTS & ENDS THREAD
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels == null) {
            BotLogger.log(event, "`#bothelper-lounge` did not have any threads open - `/game end` cannot continue");
            return;
        }
        String threadName = "game-starts-and-ends";
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                MessageHelper.sendMessageToChannel(threadChannel_,
                    "Game: **" + gameName + "** on server **" + activeGame.getGuild().getName() + "** has concluded.");
            }
        }
    }

    public static String getGameEndText(Game activeGame, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**").append(activeGame.getName()).append("**__ - ").append(activeGame.getCustomName()).append("\n");
        sb.append(activeGame.getCreationDate()).append(" - ").append(Helper.getDateRepresentation(activeGame.getLastModifiedDate()));
        sb.append("\n");
        sb.append("\n");
        sb.append("**Players:**").append("\n");
        int index = 1;
        for (Player player : activeGame.getRealPlayers()) {
            Optional<User> user = Optional.ofNullable(event.getJDA().getUserById(player.getUserID()));
            int playerVP = player.getTotalVictoryPoints();
            sb.append("> `").append(index).append(".` ");
            sb.append(player.getFactionEmoji());
            sb.append(Emojis.getColourEmojis(player.getColor())).append(" ");
            if (user.isPresent()) {
                sb.append(user.get().getAsMention());
            } else {
                sb.append(player.getUserName());
            }
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

    public static String getTIGLFormattedGameEndText(Game activeGame, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(Emojis.TIGL).append("TIGL\n\n");
        sb.append("This was a TIGL game! 👑").append(activeGame.getGameWinner().get().getPing()).append(", please [report the results](https://forms.gle/aACA16qcyG6j5NwV8):\n");
        sb.append("```\nMatch Start Date: ").append(Helper.getDateRepresentation(activeGame.getEndedDate())).append(" (TIGL wants Game End Date for Async)\n");
        sb.append("Match Start Time: 00:00\n\n");
        sb.append("Players:").append("\n");
        int index = 1;
        for (Player player : activeGame.getRealPlayers()) {
            int playerVP = player.getTotalVictoryPoints();
            Optional<User> user = Optional.ofNullable(event.getJDA().getUserById(player.getUserID()));
            sb.append("  ").append(index).append(". ");
            sb.append(player.getFaction()).append(" - ");
            if (user.isPresent()) {
                sb.append(user.get().getName());
            } else {
                sb.append(player.getUserName());
            }
            sb.append(" - ").append(playerVP).append(" VP\n");
            index++;
        }

        sb.append("\n");
        sb.append("Platform: Async\n");
        sb.append("Additional Notes: Async Game '").append(activeGame.getName());
        if (!StringUtils.isBlank(activeGame.getCustomName())) sb.append("   ").append(activeGame.getCustomName());
        sb.append("'\n```");

        return sb.toString();
    }
}
