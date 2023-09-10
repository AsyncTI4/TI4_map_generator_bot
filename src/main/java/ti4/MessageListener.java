package ti4;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.commands.fow.Whisper;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.MapFileDeleter;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!MapGenerator.readyToReceiveCommands) {
            event.replyChoice("Please try again in a moment. The bot is rebooting.", 0).queue();
            return;
        }

        try {
            AutoCompleteProvider.autoCompleteListener(event);
        } catch (Exception e) {
            String message = "Auto complete issue in event: " + event.getName() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getCommandString();
            BotLogger.log(message, e);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!MapGenerator.readyToReceiveCommands) {
            event.getInteraction().reply("Please try again in a moment. The bot is rebooting.").setEphemeral(true).queue();
            return;
        }

        String userID = event.getUser().getId();

        // CHECK IF CHANNEL IS MATCHED TO A GAME
        if (!event.getInteraction().getName().equals(Constants.HELP) && !event.getInteraction().getName().equals(Constants.STATISTICS) && event.getOption(Constants.GAME_NAME) == null) { //SKIP /help COMMANDS
            boolean isChannelOK = setActiveGame(event.getChannel(), userID, event.getName(), event.getSubcommandName());
            if (!isChannelOK) {
                event.reply("Command canceled. Execute command in correctly named channel that starts with the game name.\n> For example, for game `pbd123`, the channel name should start with `pbd123`").setEphemeral(true).queue();
                return;
            }
        }

        event.getInteraction().deferReply().queue();

        Member member = event.getMember();

        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue();
            // BotLogger.log(commandText); //TEMPORARY LOG ALL COMMANDS
        }

        CommandManager commandManager = CommandManager.getInstance();
        for (Command command : commandManager.getCommandList()) {
            if (command.accept(event)) {
                try {
                    command.execute(event);
                    command.postExecute(event);
                } catch (Exception e) {
                    String messageText = "Error trying to execute command: " + command.getActionID();
                    String errorMessage = ExceptionUtils.getMessage(e);
                    event.getHook().editOriginal(errorMessage).queue();
                    BotLogger.log(event, messageText, e);
                }
            }
        }
    }

    public static boolean setActiveGame(MessageChannel channel, String userID, String eventName, String subCommandName) {
        String channelName = channel.getName();
        GameManager gameManager = GameManager.getInstance();
        Game userActiveGame = gameManager.getUserActiveGame(userID);
        Set<String> mapList = gameManager.getGameNameToGame().keySet();

        MapFileDeleter.deleteFiles();

        String gameID = StringUtils.substringBefore(channelName, "-");
        boolean anyMatchGameExists = mapList.stream().anyMatch(map -> map.equals(gameID));
        if (!anyMatchGameExists && !(eventName.contains(Constants.SHOW_GAME) || eventName.contains(Constants.BOTHELPER) || eventName.contains(Constants.ADMIN)) && !(Constants.GAME.equals(eventName) && Constants.CREATE_GAME.equals(subCommandName))) {
            return false;
        }
        if (anyMatchGameExists && (gameManager.getUserActiveGame(userID) == null || !gameManager.getUserActiveGame(userID).getName().equals(gameID) && (gameManager.getGame(gameID) != null && (gameManager.getGame(gameID).isMapOpen() || gameManager.getGame(gameID).getPlayerIDs().contains(userID))))) {
            if (gameManager.getUserActiveGame(userID) != null && !gameManager.getUserActiveGame(userID).getName().equals(gameID)) {
//                MessageHelper.sendMessageToChannel(event.getChannel(), "Active game set to: " + gameID);
            }
            gameManager.setGameForUser(userID, gameID);
        } else if (gameManager.isUserWithActiveGame(userID)) {
            if (anyMatchGameExists && !channelName.startsWith(userActiveGame.getName())) {
                MessageHelper.sendMessageToChannel(channel, "Active game reset. Channel name indicates to have map associated with it. Please select correct active game or do action in neutral channel");
                gameManager.resetMapForUser(userID);
            }
        }
        return true;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message msg = event.getMessage();

        autoPingGames();

        if (msg.getContentRaw().startsWith("[DELETE]")) {
            msg.delete().queue();
        }

        handleFoWWhispers(event, msg);

        mapLog(event, msg);

        saveJSONInTTPGExportsChannel(event);
    }

    private void saveJSONInTTPGExportsChannel(MessageReceivedEvent event) {
        // TTPG-EXPORTS - Save attachment to ttpg_exports folder for later processing
        if ("ttpg-exports".equalsIgnoreCase(event.getChannel().getName())) {
            List<Message.Attachment> attachments = event.getMessage().getAttachments();
            if (!attachments.isEmpty() && "json".equalsIgnoreCase(attachments.get(0).getFileExtension())) { //write to file
                String currentDateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmmss"));
                String fileName = "ttpgexport_" + currentDateTime + ".json";
                String filePath =  Storage.getTTPGExportDirectory() + "/" + fileName;
                File file = new File(filePath);
                CompletableFuture<File> future = attachments.get(0).getProxy().downloadToFile(file);
                future.exceptionally(error -> { // handle possible errors
                    error.printStackTrace();
                    return null;
                });
                MessageHelper.sendMessageToChannel(event.getChannel(), "File imported as: `" + fileName + "`");
            }
        }
    }

    private void autoPingGames() {
        Game mapreference = GameManager.getInstance().getGame("finreference");
        int multiplier = 1000; //should be 1000
        if (mapreference != null && (new Date().getTime()) - mapreference.getLastTimeGamesChecked().getTime() > 10*60*multiplier) //10 minutes
        {
            mapreference.setLastTimeGamesChecked(new Date());
            GameSaveLoadManager.saveMap(mapreference);
            Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
            
            for (Game activeGame : mapList.values()) {
                if (activeGame.getAutoPingStatus() && activeGame.getAutoPingSpacer() != 0) {
                    String playerID = activeGame.getActivePlayer();
                    
                    if (playerID != null || "agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                        Player player = null;
                        if(playerID != null){
                            player = activeGame.getPlayer(playerID);
                        }
                        if (player != null || "agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                            long milliSinceLastPing = new Date().getTime() - activeGame.getLastActivePlayerPing().getTime();
                            if (milliSinceLastPing > (60*60*multiplier* activeGame.getAutoPingSpacer())) {
                                String realIdentity = null;
                                String ping = null;
                                if(player != null){
                                    realIdentity = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true);
                                    ping = realIdentity + " this is a gentle reminder that the game is waiting on you.";
                                }
                                if ("agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())){
                                    AgendaHelper.pingMissingPlayers(activeGame);
                                } else {
                                     long milliSinceLastTurnChange = new Date().getTime() - activeGame.getLastActivePlayerChange().getTime();
                                     int pingNumber = ((int)milliSinceLastTurnChange) / (60*60*multiplier* (int) activeGame.getAutoPingSpacer());
                                    if( milliSinceLastTurnChange > (60*60*multiplier* activeGame.getAutoPingSpacer()*2) ){
                                        ping = realIdentity + " this is a courtesy notice that the game AWAITS.";
                                    }
                                    if( milliSinceLastTurnChange > (60*60*multiplier* activeGame.getAutoPingSpacer()*3) ){
                                        ping = realIdentity + " this is a brusk missive stating that while you may sleep, the game never does.";
                                    }
                                    if( milliSinceLastTurnChange > (60*60*multiplier* activeGame.getAutoPingSpacer()*4) ){
                                        ping = realIdentity + " this is a sternly worded letter regarding your noted absense.";
                                    }
                                    if( milliSinceLastTurnChange > (60*60*multiplier* activeGame.getAutoPingSpacer()*5) ){
                                        ping = realIdentity + " this is a firm request that you do something to end this situation.";
                                    }
                                    if( milliSinceLastTurnChange > (60*60*multiplier* activeGame.getAutoPingSpacer()*6) ){
                                        ping = realIdentity + " Half dozen times the charm they say. Surely the game will move now ";
                                    }
                                    if(pingNumber == 7){
                                         ping = realIdentity + " I can write whatever I want here, not like anyone reads these anyways.";
                                    }
                                    if(pingNumber == 8){
                                         ping = realIdentity + " What rhymes with send burn, do you know? I don't";
                                    }
                                    if(pingNumber > 8){
                                         ping = realIdentity + " There's a rumor going around that some game is looking for a replacement player. Not that I'd know anything about that. ";
                                    }
                                    if( milliSinceLastTurnChange > (60*60*multiplier* activeGame.getAutoPingSpacer()*10) && milliSinceLastTurnChange < (60*60*multiplier* activeGame.getAutoPingSpacer()*11) && !activeGame.isFoWMode()){
                                        ping = realIdentity + " this is your final reminder. I hope you are doing well, wherever you are, and I'm sure whatever you are doing is far more important than TI. Or your name is Frox";
                                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame)+ " the game has stalled on a player, and autoping will now stop pinging them. ");
                                    }
                                    if( milliSinceLastTurnChange > (60*60*multiplier* activeGame.getAutoPingSpacer()*11) && !activeGame.isFoWMode()){
                                        continue;
                                    }
                                    if (activeGame.isFoWMode()) {
                                        MessageHelper.sendPrivateMessageToPlayer(player, activeGame, ping);
                                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),  "Active player has been pinged. This is ping #"+pingNumber);
                                    } else {
                                        MessageChannel gameChannel = activeGame.getMainGameChannel();
                                        if (gameChannel != null) {
                                            MessageHelper.sendMessageToChannel(gameChannel, ping);
                                        }
                                    }
                                }
                                
                                activeGame.setLastActivePlayerPing(new Date());
                                GameSaveLoadManager.saveMap(activeGame);
                            }
                        }
                    }else{
                        long milliSinceLastPing = new Date().getTime() - activeGame.getLastActivePlayerPing().getTime();
                        if (milliSinceLastPing > (60*60*multiplier* activeGame.getAutoPingSpacer())) {
                            if("agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())){
                                AgendaHelper.pingMissingPlayers(activeGame);
                            }
                            activeGame.setLastActivePlayerPing(new Date());
                            GameSaveLoadManager.saveMap(activeGame);
                        }
                    }
                }
            }
        }
    }

    private void handleFoWWhispers(MessageReceivedEvent event, Message msg) {
        if (msg.getContentRaw().contains("used /fow whisper")) {
            msg.delete().queue();
        }
        
        List<String> colors = Mapper.getColors();
        String message = msg.getContentRaw().toLowerCase();
        boolean messageToColor = false;
        for (String color : colors) {
            if (message.startsWith("to" + color)) {
                messageToColor = true;
                break;
            }
        }
        if (event.getChannel() instanceof ThreadChannel &&  event.getChannel().getName().contains("vs") &&  event.getChannel().getName().contains("private")) {
            String gameName = event.getChannel().getName();
            String message2 = msg.getContentRaw();
			gameName = gameName.substring(0, gameName.indexOf("-"));
			Game activeGame = GameManager.getInstance().getGame(gameName);
            if(activeGame.isFoWMode() && ((!"947763140517560331".equalsIgnoreCase(event.getAuthor().getId()) && !event.getAuthor().isBot() && !"1089270182171656292".equalsIgnoreCase(event.getAuthor().getId())) || (event.getAuthor().isBot() && message2.contains("Total hits ")))           ){
                
                for(Player player : activeGame.getRealPlayers()){
                    MessageChannel pChannel = player.getPrivateChannel();
                    TextChannel pChan = (TextChannel) pChannel;
                    if(pChan != null){
                        
                        
                        String newMessage = ButtonHelper.getTrueIdentity(player, activeGame)+" Someone said: " + message2;
                        if(event.getAuthor().isBot() && message2.contains("Total hits ")){
                            String hits = StringUtils.substringAfter(message2, "Total hits ");
                            String location = StringUtils.substringBefore(message2, "combat rolls for");
                            newMessage = ButtonHelper.getTrueIdentity(player, activeGame)+" Someone rolled dice for "+location+" and got a total of **" + hits + " hits";
                        }
                        String[] threadN = event.getChannel().getName().split("-");
                        String threadName = threadN[0]+"-"+threadN[1]+"-"+threadN[2]+"-"+threadN[3]+"-"+threadN[4];
                        List<ThreadChannel> threadChannels = pChan.getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().contains(threadName) && threadChannel_ != event.getChannel()) {
                                MessageHelper.sendMessageToChannel(threadChannel_, newMessage);
                            }
                        }
                    }
                }
                //activeMap.getActionsChannel().addReactionById(event.getChannel().getId(), emojiToUse).queue();
                
            }
            
            
        }

        if (messageToColor) {
            String gameName = event.getChannel().getName();
			gameName = gameName.substring(0, gameName.indexOf("-"));
			Game activeGame = GameManager.getInstance().getGame(gameName);
            if (activeGame.isFoWMode()) {
                String msg3 = msg.getContentRaw();
                String msg2 = msg3.substring(msg3.indexOf(" ") + 1);
                Player player = activeGame.getPlayer(event.getAuthor().getId());
                if (activeGame.isCommunityMode()) {
                    Collection<Player> players = activeGame.getPlayers().values();
                    List<Role> roles = event.getMember().getRoles();
                    for (Player player2 : players) {
                        if (roles.contains(player2.getRoleForCommunity())) {
                            player = player2;
                        }
                    }
                }
                Player player_ = activeGame.getPlayer(event.getAuthor().getId());
                String factionColor = msg3.substring(2,msg3.indexOf(" ")).toLowerCase();
                factionColor = AliasHandler.resolveFaction(factionColor);
                for (Player player3 : activeGame.getPlayers().values()) {
                    if (Objects.equals(factionColor, player3.getFaction()) ||
                            Objects.equals(factionColor, player3.getColor())) {
                        player_ = player3;
                        break;
                    }
                }

                Whisper.sendWhisper(activeGame, player, player_, msg2, "n", event.getChannel(), event.getGuild());
                msg.delete().queue();
            }
        }
    }

    private void mapLog(MessageReceivedEvent event, Message msg) {
        if (msg.getContentRaw().startsWith("map_log")) {
            if (event.isFromType(ChannelType.PRIVATE)) {
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(), event.getMessage().getContentDisplay());
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getId(), event.getMessage().getContentDisplay());
            } else {
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(), event.getChannel().asTextChannel().getName(), event.getMember().getEffectiveName(), event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(), event.getChannel().asTextChannel().getId(), event.getAuthor().getId(), event.getMessage().getContentDisplay());
            }
        }
    }
}