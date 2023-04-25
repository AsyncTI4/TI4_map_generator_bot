package ti4;

import net.dv8tion.jda.api.entities.channel.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.Role;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Storage;
import ti4.map.Map;
import ti4.map.MapFileDeleter;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.commands.fow.Whisper;
import ti4.map.Player;
import ti4.helpers.Helper;
import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.Objects;
import java.util.Collection;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.helpers.AliasHandler;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        try {
            AutoCompleteProvider.autoCompleteListener(event);
        } catch (Exception e) {
            BotLogger.log("Auto complete issue in event: " + event.getName(), e);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        event.getInteraction().deferReply().queue();
        String userID = event.getUser().getId();
        Member member = event.getMember();
        if (member != null) event.getChannel().sendMessage("```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```").queue();

        // CHECK IF CHANNEL IS MATCHED TO A GAME
        if (!event.getInteraction().getName().equals("help")) { //SKIP /help COMMANDS
            boolean isChannelOK = setActiveGame(event.getChannel(), userID, event.getName());
            if (!isChannelOK) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Command canceled. Execute command in correct channel, as game name.");
                return;
            }
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

    public static boolean setActiveGame(MessageChannel channel, String userID, String eventName) {
        String channelName = channel.getName();
        MapManager mapManager = MapManager.getInstance();
        Map userActiveMap = mapManager.getUserActiveMap(userID);
        Set<String> mapList = mapManager.getMapList().keySet();
        StringTokenizer channelNameTokenizer = new StringTokenizer(channelName, "-");


        MapFileDeleter.deleteFiles();

        String gameID = channelNameTokenizer.nextToken();
        boolean anyMatchGameExists = mapList.stream().anyMatch(map -> map.equals(gameID));
        if (!anyMatchGameExists && !(eventName.contains(Constants.SHOW_GAME) || eventName.contains(Constants.CREATE_GAME) || eventName.contains(Constants.BOTHELPER) || eventName.contains(Constants.ADMIN))) {
            return false;
        }
        if (anyMatchGameExists && (mapManager.getUserActiveMap(userID) == null || !mapManager.getUserActiveMap(userID).getName().equals(gameID) && (mapManager.getMap(gameID) != null && (mapManager.getMap(gameID).isMapOpen() || mapManager.getMap(gameID).getPlayerIDs().contains(userID))))) {
            if (mapManager.getUserActiveMap(userID) != null && !mapManager.getUserActiveMap(userID).getName().equals(gameID)) {
//                MessageHelper.sendMessageToChannel(event.getChannel(), "Active game set to: " + gameID);
            }
            mapManager.setMapForUser(userID, gameID);
        } else if (mapManager.isUserWithActiveMap(userID)) {
            if (anyMatchGameExists && !channelName.startsWith(userActiveMap.getName())) {
                MessageHelper.sendMessageToChannel(channel, "Active game reset. Channel name indicates to have map associated with it. Please select correct active game or do action in neutral channel");
                mapManager.resetMapForUser(userID);
            }
        }
        return true;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
//        CommandManager commandManager = CommandManager.getInstance();
//        for (Command command : commandManager.getCommandList()) {
//            if (command.accept(event))
//            {
//                command.execute(event);
//                String message = event.getMessage().getContentRaw();
//                LoggerHandler.logInfo(message);
//            }
//        }





        Message msg = event.getMessage();
        Map mapreference = MapManager.getInstance().getMap("finreference");
        if (mapreference != null && (new Date().getTime()) - mapreference.getLastTimeGamesChecked().getTime() > 1000*10*60) //10 minutes
        {
            mapreference.setLastTimeGamesChecked(new Date());
            MapSaveLoadManager.saveMap(mapreference);
            HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
            for (Map activeMap : mapList.values()) {
                if(activeMap.getAutoPingStatus() && activeMap.getAutoPingSpacer() != 0)
                {
                    String playerID = activeMap.getActivePlayer();
                    if (playerID != null) 
                    {
                        Player player = activeMap.getPlayer(playerID);
                        if (player != null) 
                        {
                            long milliSinceLastPing = new Date().getTime() - activeMap.getLastActivePlayerPing().getTime();
                            if (milliSinceLastPing > (1000 *60*60* activeMap.getAutoPingSpacer())) 
                            {
                                String realIdentity = "";
                                if(activeMap.isCommunityMode())
                                {
                                    if(player.getRoleForCommunity() == null)
                                    {
                                        return;
                                    }
                                    GuildChannel guildGetter = (GuildChannel) activeMap.getMainGameChannel();
                                    realIdentity = Helper.getRoleMentionByName(guildGetter.getGuild(), player.getRoleForCommunity().getName()); //need to get right guild later
                                }
                                else
                                {
                                    realIdentity = Helper.getPlayerRepresentation(player);
                                }
                                String ping = realIdentity + " this is a gentle reminder that it is your turn.";
                                if(activeMap.isFoWMode()) {
                                    MessageHelper.sendPrivateMessageToPlayer(player, activeMap, ping);
                                } else {
                                    MessageChannel gameChannel = activeMap.getMainGameChannel();
                                    if(gameChannel != null)
                                    {
                                        MessageHelper.sendMessageToChannel(gameChannel, ping);
                                    }
                                }
                                activeMap.setLastActivePlayerPing(new Date());
                                MapSaveLoadManager.saveMap(activeMap);
                            }
                        }
                    }
                }
            }
        }


        if (msg.getContentRaw().startsWith("[DELETE]")) {
            msg.delete().queue();
        }
        if (msg.getContentRaw().contains("used /fow whisper")) {
            msg.delete().queue();
        }
        if (msg.getContentRaw().toLowerCase().startsWith("tored") || msg.getContentRaw().toLowerCase().startsWith("topurple")
        || msg.getContentRaw().toLowerCase().startsWith("toblue") || msg.getContentRaw().toLowerCase().startsWith("toyellow") 
        || msg.getContentRaw().toLowerCase().startsWith("toorange") || msg.getContentRaw().toLowerCase().startsWith("togreen")) {
            
            String gameName = event.getChannel().getName();
			gameName = gameName.substring(0, gameName.indexOf("-"));
			Map map = MapManager.getInstance().getMap(gameName);
            if(map.isFoWMode())
            {
                String msg3 = msg.getContentRaw();
                String msg2 = msg3.substring(msg3.indexOf(" ")+1,msg3.length());
                Player player = map.getPlayer(event.getAuthor().getId());
                if(map.isCommunityMode())
                {
                    Collection<Player> players = map.getPlayers().values();
                    java.util.List<Role> roles = event.getMember().getRoles();
                    for (Player player2 : players) {
                        if (roles.contains(player2.getRoleForCommunity())) {
                            player = player2;
                        }
                    }
                }
                Player player_ = map.getPlayer(event.getAuthor().getId());
                String factionColor = msg3.substring(2,msg3.indexOf(" ")).toLowerCase();
                factionColor = AliasHandler.resolveFaction(factionColor);
                for (Player player3 : map.getPlayers().values()) {
                    if (Objects.equals(factionColor, player3.getFaction()) ||
                            Objects.equals(factionColor, player3.getColor())) {
                        player_ = player3;
                        break;
                    }
                }
                
                if (map != null)
                {
                    Whisper.sendWhisper(map, player, player_, msg2, "n", (MessageChannel) event.getChannel(), event.getGuild());
                }
                msg.delete().queue();
            }
        }
        if (msg.getContentRaw().startsWith("map_log")) {
            if (event.isFromType(ChannelType.PRIVATE)) {
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(), event.getMessage().getContentDisplay());
                System.out.printf("[PM] %s: %s\n", event.getAuthor().getId(), event.getMessage().getContentDisplay());
            } else {
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(), event.getChannel().asTextChannel().getName(), event.getMember().getEffectiveName(), event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(), event.getChannel().asTextChannel().getId(), event.getAuthor().getId(), event.getMessage().getContentDisplay());
            }
        }

        saveJSONInTTPGExportsChannel(event);
    }

    private void saveJSONInTTPGExportsChannel(MessageReceivedEvent event) {
        // TTPG-EXPORTS - Save attachment to ttpg_exports folder for later processing
        if (event.getChannel().getName().equalsIgnoreCase("ttpg-exports")) {
            List<Message.Attachment> attachments = event.getMessage().getAttachments();
            if (attachments.isEmpty()) {
                return; // no attachments on the message!
            } else if (!attachments.get(0).getFileExtension().equalsIgnoreCase("json")) {
                // MessageHelper.sendMessageToChannel(event.getChannel(), "File is not a JSON file. Will not be saved.");
                return;
            } else { //write to file
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
}