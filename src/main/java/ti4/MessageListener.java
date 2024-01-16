package ti4;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.MapFileDeleter;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction().reply("Please try again in a moment. The bot is is not ready to receive commands.").setEphemeral(true).queue();
            return;
        }
        long startTime = new Date().getTime();

        String userID = event.getUser().getId();

        // CHECK IF CHANNEL IS MATCHED TO A GAME
        if (!event.getInteraction().getName().equals(Constants.HELP) && !event.getInteraction().getName().equals(Constants.STATISTICS)
            && (event.getInteraction().getSubcommandName() == null || !event.getInteraction().getSubcommandName().equalsIgnoreCase(Constants.CREATE_GAME_BUTTON))
            && !event.getInteraction().getName().equals(Constants.SEARCH)
            && event.getOption(Constants.GAME_NAME) == null) { //SKIP /help COMMANDS
            boolean isChannelOK = setActiveGame(event.getChannel(), userID, event.getName(), event.getSubcommandName());
            if (!isChannelOK) {
                event
                    .reply(
                        "Command canceled. Execute command in correctly named channel that starts with the game name.\n> For example, for game `pbd123`, the channel name should start with `pbd123`")
                    .setEphemeral(true).queue();
                return;
            } else {
                Game userActiveGame = GameManager.getInstance().getUserActiveGame(userID);
                if (userActiveGame != null) {
                    userActiveGame.incrementSpecificSlashCommandCount(event.getFullCommandName());
                }
            }
        }

        event.getInteraction().deferReply().queue();

        Member member = event.getMember();
        if (member != null) {
            String commandText = "```fix\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
            event.getChannel().sendMessage(commandText).queue(m -> BotLogger.logSlashCommand(event, m));
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
        long endTime = new Date().getTime();
        if (endTime - startTime > 3000) {
            BotLogger.log(event, "This slash command took longer than 3000 ms (" + (endTime - startTime) + ")");
        }
    }

    public static boolean setActiveGame(MessageChannel channel, String userID, String eventName, String subCommandName) {
        String channelName = channel.getName();
        GameManager gameManager = GameManager.getInstance();
        Game userActiveGame = gameManager.getUserActiveGame(userID);
        Set<String> mapList = gameManager.getGameNameToGame().keySet();

        MapFileDeleter.deleteFiles();

        String gameID = StringUtils.substringBefore(channelName, "-");
        boolean gameExists = mapList.contains(gameID);
        boolean isUnprotectedCommand = eventName.contains(Constants.SHOW_GAME) || eventName.contains(Constants.BOTHELPER) || eventName.contains(Constants.ADMIN)
            || eventName.contains(Constants.DEVELOPER);
        boolean isUnprotectedCommandSubcommand = (Constants.GAME.equals(eventName) && Constants.CREATE_GAME.equals(subCommandName));
        if (!gameExists && !(isUnprotectedCommand) && !(isUnprotectedCommandSubcommand)) {
            return false;
        }
        if (gameExists && (gameManager.getUserActiveGame(userID) == null || !gameManager.getUserActiveGame(userID).getName().equals(gameID)
            && (gameManager.getGame(gameID) != null && (gameManager.getGame(gameID).isCommunityMode() || gameManager.getGame(gameID).getPlayerIDs().contains(userID))))) {
            if (gameManager.getUserActiveGame(userID) != null && !gameManager.getUserActiveGame(userID).getName().equals(gameID)) {
                // MessageHelper.sendMessageToChannel(channel, "Active game set to: " + gameID);
            }
            gameManager.setGameForUser(userID, gameID);
        } else if (gameManager.isUserWithActiveGame(userID)) {
            if (gameExists && !channelName.startsWith(userActiveGame.getName())) {
                //MessageHelper.sendMessageToChannel(channel,"Active game reset. Channel name indicates to have map associated with it. Please select correct active game or do action in neutral channel");
                gameManager.resetMapForUser(userID);
            }
        }
        return true;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!isAsyncServer(event.getGuild().getId())) return;
        long timeNow = new Date().getTime();
        try {
            Message msg = event.getMessage();
            if (msg.getContentRaw().startsWith("[DELETE]")) {
                msg.delete().queue();
            }
            autoPingGames();
            handleFoWWhispersAndFowCombats(event, msg);
            mapLog(event, msg);
            saveJSONInTTPGExportsChannel(event);
        } catch (Exception e) {
            BotLogger.log("`MessageListener.onMessageReceived`   Error trying to handle a received message:\n> " + event.getMessage().getJumpUrl(), e);
        }
        if (new Date().getTime() - timeNow > 1500) {
            BotLogger.log(event.getMessage().getChannel().getName() + " A message in this channel took longer than 1500 ms (" + (new Date().getTime() - timeNow) + ")");
        }
    }

    private void saveJSONInTTPGExportsChannel(MessageReceivedEvent event) {
        // TTPG-EXPORTS - Save attachment to ttpg_exports folder for later processing
        if ("ttpg-exports".equalsIgnoreCase(event.getChannel().getName())) {
            List<Message.Attachment> attachments = event.getMessage().getAttachments();
            if (!attachments.isEmpty() && "json".equalsIgnoreCase(attachments.get(0).getFileExtension())) { //write to file
                String currentDateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmmss"));
                String fileName = "ttpgexport_" + currentDateTime + ".json";
                String filePath = Storage.getTTPGExportDirectory() + "/" + fileName;
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
        if (mapreference != null && (new Date().getTime()) - mapreference.getLastTimeGamesChecked().getTime() > 10 * 60 * multiplier) //10 minutes
        {
            mapreference.setLastTimeGamesChecked(new Date());
            GameSaveLoadManager.saveMap(mapreference);
            Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();

            for (Game activeGame : mapList.values()) {
                if (!activeGame.isHasEnded()) {
                    Helper.checkAllSaboWindows(activeGame);
                } else {
                    continue;
                }
                long spacer = activeGame.getAutoPingSpacer();
                String playerID = activeGame.getActivePlayer();
                Player player = null;
                if (playerID != null) {
                    player = activeGame.getPlayer(playerID);
                    if (player != null && player.getPersonalPingInterval() > 0) {
                        spacer = player.getPersonalPingInterval();
                    }
                }
                if (activeGame.getAutoPingStatus() && spacer != 0 && !activeGame.getTemporaryPingDisable()) {
                    if (playerID != null || "agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())) {

                        if (player != null || "agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                            long milliSinceLastPing = new Date().getTime() - activeGame.getLastActivePlayerPing().getTime();
                            if (milliSinceLastPing > (60 * 60 * multiplier * spacer)
                                || (player != null && player.shouldPlayerBeTenMinReminded() && milliSinceLastPing > (60 * 5 * multiplier))) {
                                String realIdentity = null;
                                String ping = null;
                                if (player != null) {
                                    realIdentity = player.getRepresentation(true, true);
                                    ping = realIdentity + " this is a gentle reminder that it is your turn.";
                                }
                                if ("agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                                    AgendaHelper.pingMissingPlayers(activeGame);
                                } else {
                                    long milliSinceLastTurnChange = new Date().getTime() - activeGame.getLastActivePlayerChange().getTime();
                                    int autoPingSpacer = (int) spacer;
                                    int pingNumber = (int) (milliSinceLastTurnChange) / (60 * 60 * multiplier * (int) autoPingSpacer);
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 2)) {
                                        ping = realIdentity + " this is a courtesy notice that the game is waiting (impatiently).";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 3)) {
                                        ping = realIdentity + " this is a brusk missive stating that while you may sleep, the bot never does (and its been told to ping you about it).";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 4)) {
                                        ping = realIdentity
                                            + " this is a sternly worded letter from the bot regarding your noted absence. Do you know how much paperwork this creates? (none in reality but a lot in theory)";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 5)) {
                                        ping = realIdentity
                                            + " this is a firm request from the bot that you do something to end this situation. At this rate you will never make the top 100 fastest players";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 6)) {
                                        ping = realIdentity
                                            + " Half dozen times the charm they say. I have it on good authority that if you move in the next 10 ms, you're guaranteed to win the next combat you have, bot's honor. Wait too long though and your dice get cursed. ";
                                    }
                                    if (pingNumber == 7) {
                                        ping = realIdentity + " I can write whatever I want here, not like it's likely that you've checked in to read any of it anyways.";
                                    }
                                    if (pingNumber == 8) {
                                        ping = realIdentity + " You should end turn soon, there might be a bear on the loose, and you know which friend gets eaten by the angry bear";
                                    }
                                    if (pingNumber == 9) {
                                        ping = realIdentity
                                            + " There's a rumor going around that some game is looking for a replacement player. Not that the bot would know anything about that (who are we kidding, the bot knows everything, it just acts dumb sometimes to fool you into a state of compliance) ";
                                    }
                                    if (pingNumber == 10) {
                                        ping = realIdentity
                                            + " Do you ever wonder what we're doing here? Such a short time here on earth, and here we are, spending some of it waiting for a TI4 game to move. Well, at least some of us probably are ";
                                    }
                                    if (pingNumber == 11) {
                                        ping = realIdentity
                                            + " We should hire some monkeys to write these prompts. Then at least these reminders would be productive and maybe one day produce Shakespeare ";
                                    }
                                    if (pingNumber == 12) {
                                        ping = realIdentity + " This is lucky number 12. You wanna move now to avoid the bad luck of 13. Don't say we didn't warn you";
                                    }
                                    if (pingNumber == 13) {
                                        ping = realIdentity
                                            + " All your troops decided it was holiday leave and they went home. Good luck getting them back into combat readiness by the time you need them. ";
                                    }
                                    if (pingNumber == 14) {
                                        ping = realIdentity
                                            + " The turtles who bear the weight of the universe are going to die from old-age soon. Better pick up the pace or the game will never finish. ";
                                    }
                                    if (pingNumber == 15) {
                                        ping = realIdentity
                                            + " The turtles who bear the weight of the universe are going to die from old-age soon. Better pick up the pace or the game will never finish. ";
                                    }
                                    if (pingNumber == 16) {
                                        ping = realIdentity
                                            + " Your name is goin be put on the bot's top 10 most wanted players soon. There's currently 27 players on that list, you dont wanna join em ";
                                    }
                                    if (pingNumber == 17) {
                                        ping = realIdentity
                                            + " You thought the duplicate ping before meant that the bot had run out of things to say about how boring it is to wait this long. Shows how much you know.  ";
                                    }
                                    if (pingNumber == 18) {
                                        ping = realIdentity
                                            + " The bot's decided to start training itself to take over your turn. At its current rate of development, you have -212 days until it knows the rules better than you ";
                                    }
                                    if (pingNumber == 19) {
                                        ping = realIdentity + " They say nice guys finish last, but clearly they havent seen your track record";
                                    }
                                    if (pingNumber == 20) {
                                        ping = realIdentity + " Wait too much longer, and the bot is gonna hire some Cabal hit-men to start rifting your ships.";
                                    }
                                    if (pingNumber == 21) {
                                        ping = realIdentity + " Supposedly great things come to those who wait. If thats true, you owe the bot something roughly the size of Mount Everest";
                                    }
                                    if (pingNumber == 22) {
                                        ping = realIdentity + " Knock knock";
                                    }
                                    if (pingNumber == 23) {
                                        ping = realIdentity + " Who's there?";
                                    }
                                    if (pingNumber == 24) {
                                        ping = realIdentity + " It sure aint you";
                                    }
                                    if (pingNumber == 25) {
                                        ping = realIdentity + " I apologize, we bots dont have much of a sense of humor, but who knows, maybe you would have laughed if you were here ;_;";
                                    }

                                    int maxSoFar = 25;
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * maxSoFar)) {
                                        ping = realIdentity + " Rumors of the bot running out of stamina are greatly exaggerated. The bot will win this stare-down, it is simply a matter of time. ";
                                    }
                                    if (pingNumber > maxSoFar + 1 && !activeGame.isFoWMode()) {
                                        continue;
                                    }
                                    if (pingNumber == maxSoFar + 2 && !activeGame.isFoWMode()) {
                                        ping = realIdentity + " this is your final reminder. Stopping pinging now so we dont come back in 2 months and find 600+ messages";
                                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                                            activeGame.getPing() + " the game has stalled on a player, and autoping will now stop pinging them. ");
                                    }

                                    if (activeGame.isFoWMode()) {
                                        MessageHelper.sendPrivateMessageToPlayer(player, activeGame, ping);
                                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Active player has been pinged. This is ping #" + pingNumber);
                                    } else {
                                        MessageChannel gameChannel = activeGame.getMainGameChannel();
                                        if (gameChannel != null) {
                                            MessageHelper.sendMessageToChannel(gameChannel, ping);
                                            if (ping != null && ping.contains("courtesy notice")) {
                                                List<Button> buttons = new ArrayList<>();
                                                buttons.add(Button.danger("temporaryPingDisable", "Disable Pings For Turn"));
                                                buttons.add(Button.secondary("deleteButtons", "Delete These Buttons"));
                                                MessageHelper.sendMessageToChannelWithButtons(gameChannel, realIdentity
                                                    + " if the game is not waiting on you, you can disable the auto ping for this turn so it doesnt annoy you. It will turn back on for the next turn.",
                                                    buttons);
                                            }
                                        }
                                    }
                                }
                                if (player != null) {
                                    player.setWhetherPlayerShouldBeTenMinReminded(false);
                                }
                                activeGame.setLastActivePlayerPing(new Date());
                                GameSaveLoadManager.saveMap(activeGame);
                            }
                        }
                    } else {
                        long milliSinceLastPing = new Date().getTime() - activeGame.getLastActivePlayerPing().getTime();
                        if (milliSinceLastPing > (60 * 60 * multiplier * activeGame.getAutoPingSpacer())) {
                            if ("agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())) {
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

    private void handleFoWWhispersAndFowCombats(MessageReceivedEvent event, Message msg) {
        // if(event.getChannel().getName().contains("-actions") && !event.getAuthor().isBot() ){
        //     try{
        //             String gameName = event.getChannel().getName().substring(0,  event.getChannel().getName().indexOf("-"));
        //             Game activeGame = GameManager.getInstance().getGame(gameName);
        //             if(activeGame != null && activeGame.getPublicObjectives1() != null && activeGame.getPublicObjectives1().size() > 1 && activeGame.getBotShushing()){
        //                 MessageHistory mHistory = event.getChannel().getHistory();
        //                 RestAction<List<Message>> lis = mHistory.retrievePast(4);
        //                 boolean allNonBots = true;
        //                 for(Message m : lis.complete()){
        //                     if(m.getAuthor().isBot() || m.getReactions().size() > 0){
        //                         allNonBots = false;
        //                         break;
        //                     }
        //                 }
        //                 if(allNonBots){
        //                     event.getChannel().addReactionById(event.getMessageId(), Emoji.fromFormatted("<:Actions_Channel:1154220656695713832>")).queue();
        //                 }
        //             }
        //         }catch (Exception e){
        //             BotLogger.log("Reading previous message", e);
        //         }
        //    // event.getChannel().addReactionById(event.getMessageId(), Emoji.fromFormatted("<:this_is_the_actions_channel:1152245957489082398>")).queue();
        // }

        if (!event.getAuthor().isBot() && event.getChannel().getName().contains("-")) {
            String gameName = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));

            Game activeGame = GameManager.getInstance().getGame(gameName);
            if (activeGame != null && activeGame.getBotFactionReacts() && !activeGame.isFoWMode()) {
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
                try {
                    MessageHistory mHistory = event.getChannel().getHistory();
                    RestAction<List<Message>> lis = mHistory.retrievePast(2);
                    if (!event.getMessage().getAuthor().getId().equalsIgnoreCase(lis.complete().get(1).getAuthor().getId())) {
                        if (player != null && player.isRealPlayer()) {
                            event.getChannel().addReactionById(event.getMessageId(), Emoji.fromFormatted(player.getFactionEmoji())).queue();
                        }
                    }
                } catch (Exception e) {
                    BotLogger.log("Reading previous message", e);
                }
            }
        }

        if (msg.getContentRaw().contains("used /fow whisper")) {
            msg.delete().queue();
        }

        List<String> colors = Mapper.getColors();
        colors.addAll(Mapper.getFactionIDs());
        String message = msg.getContentRaw().toLowerCase();
        boolean messageToColor = false;
        boolean messageToFutureColor = false;
        boolean messageToMyself = false;
        boolean messageToJazz = false;
        for (String color : colors) {
            if (message.startsWith("to" + color)) {
                messageToColor = true;
                break;
            }
            if (message.startsWith("tofuture" + color)) {
                messageToFutureColor = true;
                break;
            }
        }
        if (message.startsWith("tofutureme")) {
            messageToMyself = true;
        }
        if (message.startsWith("tojazz") || message.startsWith("tofuturejazz")) {
            messageToJazz = true;
        }

        if (event.getChannel() instanceof ThreadChannel && event.getChannel().getName().contains("vs") && event.getChannel().getName().contains("private")) {
            String gameName2 = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));
            String message2 = msg.getContentRaw();

            Game activeGame = GameManager.getInstance().getGame(gameName2);
            Player player3 = activeGame.getPlayer(event.getAuthor().getId());
            if (activeGame.isCommunityMode()) {
                Collection<Player> players = activeGame.getPlayers().values();
                List<Role> roles = event.getMember().getRoles();
                for (Player player2 : players) {
                    if (roles.contains(player2.getRoleForCommunity())) {
                        player3 = player2;
                    }
                }
            }

            if (activeGame.isFoWMode() &&
                ((player3 != null && player3.isRealPlayer() && event.getChannel().getName().contains(player3.getColor()) && !event.getAuthor().isBot())
                    || (event.getAuthor().isBot() && message2.contains("Total hits ")))) {

                String systemPos;
                if (StringUtils.countMatches(event.getChannel().getName(), "-") > 4) {
                    systemPos = event.getChannel().getName().split("-")[4];
                } else {
                    return;
                }
                Tile tile = activeGame.getTileByPosition(systemPos);
                for (Player player : activeGame.getRealPlayers()) {
                    if (player3 != null && player == player3) {
                        continue;
                    }
                    if (!tile.getRepresentationForButtons(activeGame, player).contains("(")) {
                        continue;
                    }
                    MessageChannel pChannel = player.getPrivateChannel();
                    TextChannel pChan = (TextChannel) pChannel;
                    if (pChan != null) {
                        String newMessage = player.getRepresentation(true, true) + " Someone said: " + message2;
                        if (event.getAuthor().isBot() && message2.contains("Total hits ")) {
                            String hits = StringUtils.substringAfter(message2, "Total hits ");
                            String location = StringUtils.substringBefore(message2, "rolls for");
                            newMessage = player.getRepresentation(true, true) + " Someone rolled dice for " + location + " and got a total of **" + hits + " hits";
                        }
                        if (!event.getAuthor().isBot() && player3 != null && player3.isRealPlayer()) {
                            newMessage = player.getRepresentation(true, true) + " " + StringUtils.capitalize(player3.getColor()) + " said: " + message2;
                        }

                        newMessage = newMessage.replace("Total hits", "");
                        String threadName = event.getChannel().getName();
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

        if (messageToColor || messageToMyself || messageToFutureColor || messageToJazz) {
            String gameName = event.getChannel().getName();
            gameName = gameName.replace("Cards Info-", "");
            gameName = gameName.substring(0, gameName.indexOf("-"));
            Game activeGame = GameManager.getInstance().getGame(gameName);
            if (activeGame != null) {
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

                String jazzId = "228999251328368640";
                if (messageToJazz && activeGame.getRealPlayerIDs().contains(jazzId)) {
                    if (player_.getUserID().equals(jazzId)) {
                        messageToMyself = true;
                    } else {
                        if (message.startsWith("tofuture")) {
                            messageToFutureColor = true;
                        } else {
                            messageToColor = true;
                        }
                    }
                }

                if (messageToColor) {
                    String factionColor = msg3.substring(2, msg3.indexOf(" ")).toLowerCase();
                    factionColor = AliasHandler.resolveFaction(factionColor);
                    for (Player player3 : activeGame.getRealPlayers()) {
                        if (Objects.equals(factionColor, player3.getFaction()) ||
                            Objects.equals(factionColor, player3.getColor())) {
                            player_ = player3;
                            break;
                        }
                        if (player3.getUserID().equals("228999251328368640") && messageToJazz) {
                            player_ = player3;
                            break;
                        }
                    }

                    Whisper.sendWhisper(activeGame, player, player_, msg2, "n", event.getChannel(), event.getGuild());
                } else if (messageToMyself) {
                    String previousThoughts = "";
                    if (!activeGame.getFactionsThatReactedToThis("futureMessageFor" + player.getFaction()).isEmpty()) {
                        previousThoughts = activeGame.getFactionsThatReactedToThis("futureMessageFor" + player.getFaction()) + ". ";
                    }
                    activeGame.setCurrentReacts("futureMessageFor" + player.getFaction(), previousThoughts + msg2.replace(":", "666fin"));
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " sent themselves a future message");
                } else {
                    String factionColor = msg3.substring(8, msg3.indexOf(" ")).toLowerCase();
                    factionColor = AliasHandler.resolveFaction(factionColor);
                    for (Player player3 : activeGame.getPlayers().values()) {
                        if (Objects.equals(factionColor, player3.getFaction()) ||
                            Objects.equals(factionColor, player3.getColor())) {
                            player_ = player3;
                            break;
                        }
                        if (player3.getUserID().equals("228999251328368640") && messageToJazz) {
                            player_ = player3;
                            break;
                        }
                    }
                    activeGame.setCurrentReacts("futureMessageFor_" + player_.getFaction() + "_" + player.getFaction(),
                        activeGame.getFactionsThatReactedToThis("futureMessageFor_" + player_.getFaction() + "_" + player.getFaction()) + " " + msg2.replace(":", "666fin"));
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " sent someone else a future message");
                }
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
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(), event.getChannel().asTextChannel().getName(), event.getMember().getEffectiveName(),
                    event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(), event.getChannel().asTextChannel().getId(), event.getAuthor().getId(), event.getMessage().getContentDisplay());
            }
        }
    }

    public static boolean isAsyncServer(String guildID) {
        for (Guild guild : AsyncTI4DiscordBot.guilds) {
            if (guild.getId().equals(guildID)) return true;
        }
        return false;
    }
}