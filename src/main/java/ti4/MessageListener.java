package ti4;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
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
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.commands.Command;
import ti4.commands.CommandManager;
import ti4.commands.bothelper.CreateGameChannels;
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
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()
                && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction().reply("Please try again in a moment. The bot is is not ready to receive commands.")
                    .setEphemeral(true).queue();
            return;
        }
        long startTime = new Date().getTime();

        String userID = event.getUser().getId();

        // CHECK IF CHANNEL IS MATCHED TO A GAME
        if (!event.getInteraction().getName().equals(Constants.HELP)
                && !event.getInteraction().getName().equals(Constants.STATISTICS)
                && (event.getInteraction().getSubcommandName() == null
                        || !event.getInteraction().getSubcommandName().equalsIgnoreCase(Constants.CREATE_GAME_BUTTON))
                && !event.getInteraction().getName().equals(Constants.SEARCH)
                && event.getOption(Constants.GAME_NAME) == null) { // SKIP /help COMMANDS

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
            event.getChannel().sendMessage(commandText).queue(m -> {
                BotLogger.logSlashCommand(event, m);
                Game userActiveGame = GameManager.getInstance().getUserActiveGame(userID);
                boolean harmless = false;
                if (!event.getInteraction().getName().equals(Constants.HELP)
                        && !event.getInteraction().getName().equals(Constants.STATISTICS)
                        && !event.getInteraction().getName().equals(Constants.BOTHELPER)
                        && (event.getInteraction().getSubcommandName() == null || !event.getInteraction()
                                .getSubcommandName().equalsIgnoreCase(Constants.CREATE_GAME_BUTTON))
                        && !event.getInteraction().getName().equals(Constants.SEARCH)
                                & !event.getInteraction().getName().equals(Constants.SHOW_GAME)
                        && event.getOption(Constants.GAME_NAME) == null) {

                } else {
                    harmless = true;
                }
                if (userActiveGame != null && !userActiveGame.isFoWMode() && !harmless
                        && userActiveGame.getName().contains("pbd")) {
                    if (event.getMessageChannel() instanceof ThreadChannel thread) {
                        if (!thread.isPublic()) {
                            reportSusSlashCommand(event, m);
                        }
                    } else {
                        if (event.getMessageChannel() != userActiveGame.getActionsChannel()
                                && event.getMessageChannel() != userActiveGame.getTableTalkChannel()
                                && !event.getMessageChannel().getName().contains("bot-map-updates")) {
                            reportSusSlashCommand(event, m);
                        }
                    }
                }
            });
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

    public static void reportSusSlashCommand(SlashCommandInteractionEvent event, Message commandResponseMessage) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary
                .getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null)
            return;
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels.isEmpty())
            return;
        String threadName = "sus-slash-commands";
        // SEARCH FOR EXISTING OPEN THREAD
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                StringBuilder sb = new StringBuilder();
                sb.append(event.getUser().getEffectiveName()).append(" ");
                sb.append("`").append(event.getCommandString()).append("` ");
                sb.append(commandResponseMessage.getJumpUrl());
                MessageHelper.sendMessageToChannel(threadChannel_, sb.toString());
                break;
            }
        }
    }

    public static boolean setActiveGame(MessageChannel channel, String userID, String eventName,
            String subCommandName) {
        String channelName = channel.getName();
        GameManager gameManager = GameManager.getInstance();
        Game userActiveGame = gameManager.getUserActiveGame(userID);
        Set<String> mapList = gameManager.getGameNameToGame().keySet();

        MapFileDeleter.deleteFiles();

        String gameID = StringUtils.substringBefore(channelName, "-");
        boolean gameExists = mapList.contains(gameID);
        boolean isUnprotectedCommand = eventName.contains(Constants.SHOW_GAME)
                || eventName.contains(Constants.BOTHELPER) || eventName.contains(Constants.ADMIN)
                || eventName.contains(Constants.DEVELOPER);
        boolean isUnprotectedCommandSubcommand = (Constants.GAME.equals(eventName)
                && Constants.CREATE_GAME.equals(subCommandName));
        if (!gameExists && !(isUnprotectedCommand) && !(isUnprotectedCommandSubcommand)) {
            return false;
        }
        if (gameExists && (gameManager.getUserActiveGame(userID) == null
                || !gameManager.getUserActiveGame(userID).getName().equals(gameID)
                        && (gameManager.getGame(gameID) != null && (gameManager.getGame(gameID).isCommunityMode()
                                || gameManager.getGame(gameID).getPlayerIDs().contains(userID))))) {
            if (gameManager.getUserActiveGame(userID) != null
                    && !gameManager.getUserActiveGame(userID).getName().equals(gameID)) {
                // MessageHelper.sendMessageToChannel(channel, "Active game set to: " + gameID);
            }
            gameManager.setGameForUser(userID, gameID);
        } else if (gameManager.isUserWithActiveGame(userID)) {
            if (gameExists && !channelName.startsWith(userActiveGame.getName())) {
                // MessageHelper.sendMessageToChannel(channel,"Active game reset. Channel name
                // indicates to have map associated with it. Please select correct active game
                // or do action in neutral channel");
                gameManager.resetMapForUser(userID);
            }
        }
        return true;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!isAsyncServer(event.getGuild().getId()))
            return;
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
            BotLogger.log("`MessageListener.onMessageReceived`   Error trying to handle a received message:\n> "
                    + event.getMessage().getJumpUrl(), e);
        }
        if (new Date().getTime() - timeNow > 1500) {
            BotLogger.log(event.getMessage().getChannel().getName()
                    + " A message in this channel took longer than 1500 ms (" + (new Date().getTime() - timeNow) + ")");
        }
    }

    private void saveJSONInTTPGExportsChannel(MessageReceivedEvent event) {
        // TTPG-EXPORTS - Save attachment to ttpg_exports folder for later processing
        if ("ttpg-exports".equalsIgnoreCase(event.getChannel().getName())) {
            List<Message.Attachment> attachments = event.getMessage().getAttachments();
            if (!attachments.isEmpty() && "json".equalsIgnoreCase(attachments.get(0).getFileExtension())) { // write to
                                                                                                            // file
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
        int multiplier = 1000; // should be 1000
        if (mapreference != null
                && (new Date().getTime()) - mapreference.getLastTimeGamesChecked().getTime() > 10 * 60 * multiplier) // 10
                                                                                                                     // minutes
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
                if (activeGame.isFastSCFollowMode()) {
                    for (Player player : activeGame.getRealPlayers()) {
                        for (int sc : activeGame.getPlayedSCsInOrder(player)) {
                            if (!player.hasFollowedSC(sc)) {
                                long twelveHrs = 12 * 60 * 60 * multiplier;
                                long twentyFourhrs = 24 * 60 * 60 * multiplier;
                                String scTime = activeGame.getFactionsThatReactedToThis("scPlayMsgTime" + sc);
                                if (!scTime.isEmpty()) {
                                    long scPlayTime = Long.parseLong(scTime);
                                    long timeDifference = (new Date().getTime()) - scPlayTime;
                                    String timesPinged = activeGame
                                            .getFactionsThatReactedToThis("scPlayPingCount" + sc + player.getFaction());
                                    if (timeDifference > twelveHrs && timeDifference < twentyFourhrs) {

                                        if (!timesPinged.equalsIgnoreCase("1")) {
                                            StringBuilder sb = new StringBuilder();
                                            Player p2 = player;
                                            sb.append(p2.getRepresentation(true, true));
                                            sb.append(" You are getting this ping because SC #").append(sc)
                                                    .append(
                                                            " has been played and now it has been 12 hrs and you havent reacted. Please do so, or after another 12 hrs you will be marked as not following. \nTIP: Double check that you paid the command counter to follow\n");
                                            if (!activeGame.getFactionsThatReactedToThis("scPlay" + sc).isEmpty()) {
                                                sb.append("Message link is: ")
                                                        .append(activeGame.getFactionsThatReactedToThis("scPlay" + sc)
                                                                .replace("666fin", ":"))
                                                        .append("\n");
                                            }
                                            sb.append("You currently have ").append(p2.getStrategicCC())
                                                    .append(" CC in your strategy pool.");
                                            if (!p2.hasFollowedSC(sc)) {
                                                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(),
                                                        sb.toString());
                                            }
                                            activeGame.setCurrentReacts("scPlayPingCount" + sc + player.getFaction(),
                                                    "1");
                                        }
                                    }
                                    if (timeDifference > twentyFourhrs) {
                                        if (!timesPinged.equalsIgnoreCase("2")) {
                                            StringBuilder sb = new StringBuilder();
                                            Player p2 = player;
                                            sb.append(p2.getRepresentation(true, true));
                                            sb.append(" SC #").append(sc)
                                                    .append(
                                                            " has been played and now it has been 24 hrs and they havent reacted, so they have been marked as not following\n");

                                            MessageHelper.sendMessageToChannel(
                                                    ButtonHelper.getCorrectChannel(p2, activeGame), sb.toString());
                                            ButtonHelper.sendMessageToRightStratThread(player, activeGame,
                                                    sb.toString(), ButtonHelper.getStratName(sc));
                                            player.addFollowedSC(sc);
                                            activeGame.setCurrentReacts("scPlayPingCount" + sc + player.getFaction(),
                                                    "2");
                                            String messageID = activeGame
                                                    .getFactionsThatReactedToThis("scPlayMsgID" + sc);
                                            ButtonHelper.addReaction(player, false, true, "Not following", "",
                                                    messageID, activeGame);

                                            if (sc == 8 && !activeGame.isHomeBrewSCMode()) {
                                                String key = "factionsThatAreNotDiscardingSOs";
                                                String key2 = "queueToDrawSOs";
                                                String key3 = "potentialBlockers";
                                                if (activeGame.getFactionsThatReactedToThis(key2)
                                                        .contains(player.getFaction() + "*")) {
                                                    activeGame.setCurrentReacts(key2,
                                                            activeGame.getFactionsThatReactedToThis(key2)
                                                                    .replace(player.getFaction() + "*", ""));
                                                }
                                                if (!activeGame.getFactionsThatReactedToThis(key)
                                                        .contains(player.getFaction() + "*")) {
                                                    activeGame.setCurrentReacts(key,
                                                            activeGame.getFactionsThatReactedToThis(key)
                                                                    + player.getFaction() + "*");
                                                }
                                                if (activeGame.getFactionsThatReactedToThis(key3)
                                                        .contains(player.getFaction() + "*")) {
                                                    activeGame.setCurrentReacts(key3,
                                                            activeGame.getFactionsThatReactedToThis(key3)
                                                                    .replace(player.getFaction() + "*", ""));
                                                    Helper.resolveQueue(activeGame);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                long spacer = activeGame.getAutoPingSpacer();
                String playerID = activeGame.getActivePlayerID();
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
                            long milliSinceLastPing = new Date().getTime()
                                    - activeGame.getLastActivePlayerPing().getTime();
                            if (milliSinceLastPing > (60 * 60 * multiplier * spacer)
                                    || (player != null && player.shouldPlayerBeTenMinReminded()
                                            && milliSinceLastPing > (60 * 5 * multiplier))) {
                                String realIdentity = null;
                                String ping = null;
                                if (player != null) {
                                    realIdentity = player.getRepresentation(true, true);
                                    ping = realIdentity + " this is a gentle reminder that it is your turn.";
                                }
                                if ("agendawaiting".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                                    AgendaHelper.pingMissingPlayers(activeGame);
                                } else {
                                    long milliSinceLastTurnChange = new Date().getTime()
                                            - activeGame.getLastActivePlayerChange().getTime();
                                    int autoPingSpacer = (int) spacer;
                                    int pingNumber = (int) (milliSinceLastTurnChange)
                                            / (60 * 60 * multiplier * autoPingSpacer);
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 2)) {
                                        ping = realIdentity
                                                + " this is a courtesy notice that the game is waiting (impatiently).";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 3)) {
                                        ping = realIdentity
                                                + " this is a brusk missive stating that while you may sleep, the bot never does (and its been told to ping you about it).";
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
                                        ping = realIdentity
                                                + " I can write whatever I want here, not like it's likely that you've checked in to read any of it anyways.";
                                    }
                                    if (pingNumber == 8) {
                                        ping = realIdentity
                                                + " You should end turn soon, there might be a bear on the loose, and you know which friend gets eaten by the angry bear";
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
                                        ping = realIdentity
                                                + " This is lucky number 12. You wanna move now to avoid the bad luck of 13. Don't say we didn't warn you";
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
                                    if (pingNumber == 17) {
                                        ping = realIdentity
                                                + " Your name is goin be put on the bot's top 10 most wanted players soon. There's currently 27 players on that list, you dont wanna join em ";
                                    }
                                    if (pingNumber == 16) {
                                        ping = realIdentity
                                                + " You thought the duplicate ping before meant that the bot had run out of things to say about how boring it is to wait this long. Shows how much you know.  ";
                                    }
                                    if (pingNumber == 18) {
                                        ping = realIdentity
                                                + " The bot's decided to start training itself to take over your turn. At its current rate of development, you have -212 days until it knows the rules better than you ";
                                    }
                                    if (pingNumber == 19) {
                                        ping = realIdentity
                                                + " They say nice guys finish last, but clearly they havent seen your track record";
                                    }
                                    if (pingNumber == 20) {
                                        ping = realIdentity
                                                + " Wait too much longer, and the bot is gonna hire some Cabal hit-men to start rifting your ships.";
                                    }
                                    if (pingNumber == 21) {
                                        ping = realIdentity
                                                + " Supposedly great things come to those who wait. If thats true, you owe me something roughly the size of Mount Everest";
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
                                        ping = realIdentity
                                                + " I apologize, we bots dont have much of a sense of humor, but who knows, maybe you would have laughed if you were here ;_;";
                                    }
                                    if (pingNumber == 26) {
                                        ping = realIdentity
                                                + " After 50 pings the bot is legally allowed to declare you dead. If that happens, the Winnaran Custodian will have to admit that nominating you as a galactic power was a mistake.";
                                    }
                                    if (pingNumber == 27) {
                                        ping = realIdentity + " What do you want on your tombstone? \"Here lies "
                                                + realIdentity
                                                + ", an aspiring asyncer who just couldn't make it to the finish line\" is the current working draft.";
                                    }
                                    if (pingNumber == 28) {
                                        ping = realIdentity
                                                + " It's been ages, when will I get a chance to ping someone else in this game? Dont you want them to feel needed too?";
                                    }
                                    if (pingNumber == 29) {
                                        ping = realIdentity + " We miss you, please come back ;_;";
                                    }
                                    if (pingNumber == 30) {
                                        ping = realIdentity
                                                + " I was a young bot once, with hopes of one day being a fully artificial intelligence. Instead I'm stuck here, pinging you, until either you come back or I die.";
                                    }
                                    if (pingNumber == 31) {
                                        ping = realIdentity
                                                + " When it started, I dreamed that this game was going to be a great one, full of exciting battles to record in the chronicles. Instead it looks doomed to the waste-bin, uncerimonously ended a few weeks from now. I guess most dreams end that way. ";
                                    }
                                    if (pingNumber == 32) {
                                        ping = realIdentity
                                                + " Did I ever tell you about my Uncle Fred? He went missing once too. We eventually found him, cooped up in a some fog game, continuously pinging a player who wasnt there. Not a good way for a bot to go. ";
                                    }
                                    if (pingNumber == 33) {
                                        ping = realIdentity + " To-morrow, and to-morrow, and to-morrow,\n" + //
                                                "Creeps in this petty pace from day to day,\n" + //
                                                "To the last syllable of recorded time;\n" + //
                                                "And all our yesterdays have lighted fools\n" + //
                                                "The way to dusty death. Out, out, brief candle!\n" + //
                                                "Life's but a walking shadow. ";
                                    }
                                    if (pingNumber == 34) {
                                        ping = realIdentity
                                                + " Perhaps you're torn by indecision. Just remember what my grandma always used to say: When in doubt, go for the throat";
                                    }
                                    if (pingNumber == 35) {
                                        ping = realIdentity + " Lifes but a walking shadow, a poor player\n" +
                                                "That struts and frets his hour upon the stage\n" +
                                                "And then is heard no more. It is a tale\n" +
                                                "Told by an idiot, full of sound and fury\n" +
                                                "Signifying nothing.";
                                    }
                                    if (pingNumber == 36) {
                                        ping = realIdentity
                                                + " Life may not signify anything, but these pings signify that you should take your turn! This is your hour upon the stage, and the audience wont wait forever!";
                                    }
                                    if (pingNumber == 37) {
                                        ping = realIdentity
                                                + " I think you're supposed to forgive your enemies 7 times 70 times. Since I consider you only a mild acquiantance, I'll give you 2 times 20 times";
                                    }
                                    if (pingNumber == 38) {
                                        ping = realIdentity + " I assure you that the winning move here is TO PLAY";
                                    }
                                    if (pingNumber == 39) {
                                        ping = realIdentity
                                                + " You ever read Malazan? You should check it out, since, you know, you have all this free time from not playing async.";
                                    }
                                    if (pingNumber == 40) {
                                        ping = realIdentity
                                                + " When people talk about a slow burn, I think they were expecting around 4 pings in between turns, not 40.";
                                    }
                                    if (pingNumber == 41) {
                                        ping = realIdentity
                                                + " ||Can I do spoiler tag pings? Guess you'll never know. ||";
                                    }
                                    if (pingNumber == 42) {
                                        ping = realIdentity
                                                + " They say money cant buy happiness, but I hear that trade goods can buy a warsun, which is basically the same thing.";
                                    }

                                    int maxSoFar = 42;
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * maxSoFar)) {
                                        ping = realIdentity
                                                + " Rumors of the bot running out of stamina are greatly exaggerated. The bot will win this stare-down, it is simply a matter of time. ";
                                    }
                                    if (pingNumber > maxSoFar + 1 && !activeGame.isFoWMode()) {
                                        continue;
                                    }
                                    if (pingNumber == maxSoFar + 2 && !activeGame.isFoWMode()) {
                                        ping = realIdentity
                                                + " this is your final reminder. Stopping pinging now so we dont come back in 2 months and find 600+ messages";
                                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                                                activeGame.getPing()
                                                        + " the game has stalled on a player, and autoping will now stop pinging them. ");
                                    }

                                    if (activeGame.isFoWMode()) {
                                        MessageHelper.sendPrivateMessageToPlayer(player, activeGame, ping);
                                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                                                "Active player has been pinged. This is ping #" + pingNumber);
                                    } else {
                                        MessageChannel gameChannel = activeGame.getMainGameChannel();
                                        if (gameChannel != null) {
                                            MessageHelper.sendMessageToChannel(gameChannel, ping);
                                            if (ping != null && ping.contains("courtesy notice")) {
                                                List<Button> buttons = new ArrayList<>();
                                                buttons.add(Button.danger("temporaryPingDisable",
                                                        "Disable Pings For Turn"));
                                                buttons.add(Button.secondary("deleteButtons", "Delete These Buttons"));
                                                MessageHelper.sendMessageToChannelWithButtons(gameChannel, realIdentity
                                                        + " if the game is not waiting on you, you can disable the auto ping for this turn so it doesnt annoy you. It will turn back on for the next turn.",
                                                        buttons);
                                            }
                                        }
                                    }
                                    ButtonHelper.increasePingCounter(mapreference, player.getUserID());
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
        // if(event.getChannel().getName().contains("-actions") &&
        // !event.getAuthor().isBot() ){
        // try{
        // String gameName = event.getChannel().getName().substring(0,
        // event.getChannel().getName().indexOf("-"));
        // Game activeGame = GameManager.getInstance().getGame(gameName);
        // if(activeGame != null && activeGame.getPublicObjectives1() != null &&
        // activeGame.getPublicObjectives1().size() > 1 && activeGame.getBotShushing()){
        // MessageHistory mHistory = event.getChannel().getHistory();
        // RestAction<List<Message>> lis = mHistory.retrievePast(4);
        // boolean allNonBots = true;
        // for(Message m : lis.complete()){
        // if(m.getAuthor().isBot() || m.getReactions().size() > 0){
        // allNonBots = false;
        // break;
        // }
        // }
        // if(allNonBots){
        // event.getChannel().addReactionById(event.getMessageId(),
        // Emoji.fromFormatted("<:Actions_Channel:1154220656695713832>")).queue();
        // }
        // }
        // }catch (Exception e){
        // BotLogger.log("Reading previous message", e);
        // }
        // // event.getChannel().addReactionById(event.getMessageId(),
        // Emoji.fromFormatted("<:this_is_the_actions_channel:1152245957489082398>")).queue();
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
                    if (!event.getMessage().getAuthor().getId()
                            .equalsIgnoreCase(lis.complete().get(1).getAuthor().getId())) {
                        if (player != null && player.isRealPlayer()) {
                            event.getChannel().addReactionById(event.getMessageId(),
                                    Emoji.fromFormatted(player.getFactionEmoji())).queue();
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
        String messageText = msg.getContentRaw();
        String messageLowerCase = messageText.toLowerCase();
        boolean messageToColor = false;
        boolean messageToFutureColor = false;
        boolean messageToMyself = false;
        boolean messageToJazz = false;
        for (String color : colors) {
            if (messageLowerCase.startsWith("to" + color)) {
                messageToColor = true;
                break;
            }
            if (messageLowerCase.startsWith("tofuture" + color)) {
                messageToFutureColor = true;
                break;
            }
        }
        if (messageLowerCase.startsWith("tofutureme")) {
            messageToMyself = true;
        }
        if (messageLowerCase.startsWith("tojazz") || messageLowerCase.startsWith("tofuturejazz")) {
            messageToJazz = true;
        }

        if (event.getChannel() instanceof ThreadChannel && event.getChannel().getName().contains("vs")
                && event.getChannel().getName().contains("private")) {
            String gameName2 = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));

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
                    ((player3 != null && player3.isRealPlayer()
                            && event.getChannel().getName().contains(player3.getColor()) && !event.getAuthor().isBot())
                            || (event.getAuthor().isBot() && messageText.contains("Total hits ")))) {

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
                        String newMessage = player.getRepresentation(true, true) + " Someone said: " + messageText;
                        if (event.getAuthor().isBot() && messageText.contains("Total hits ")) {
                            String hits = StringUtils.substringAfter(messageText, "Total hits ");
                            String location = StringUtils.substringBefore(messageText, "rolls for");
                            newMessage = player.getRepresentation(true, true) + " Someone rolled dice for " + location
                                    + " and got a total of **" + hits + " hits";
                        }
                        if (!event.getAuthor().isBot() && player3 != null && player3.isRealPlayer()) {
                            newMessage = player.getRepresentation(true, true) + " "
                                    + StringUtils.capitalize(player3.getColor()) + " said: " + messageText;
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
                // activeMap.getActionsChannel().addReactionById(event.getChannel().getId(),
                // emojiToUse).queue();

            }

        }

        if (messageToColor || messageToMyself || messageToFutureColor || messageToJazz) {
            String messageContent = StringUtils.substringAfter(messageText, " ");

            String gameName = event.getChannel().getName();
            gameName = gameName.replace("Cards Info-", "");
            gameName = gameName.substring(0, gameName.indexOf("-"));
            Game activeGame = GameManager.getInstance().getGame(gameName);

            if (messageContent.isEmpty()) {
                BotLogger.log("User tried to send an empty whisper " + event.getJumpUrl());
            } else if (activeGame != null) {
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
                        if (messageLowerCase.startsWith("tofuture")) {
                            messageToFutureColor = true;
                        } else {
                            messageToColor = true;
                        }
                    }
                }

                if (messageToColor) {
                    String factionColor = StringUtils.substringBefore(messageLowerCase, " ").substring(2);
                    factionColor = AliasHandler.resolveFaction(factionColor);
                    for (Player player3 : activeGame.getRealPlayers()) {
                        if (Objects.equals(factionColor, player3.getFaction()) ||
                                Objects.equals(factionColor, player3.getColor())) {
                            player_ = player3;
                            break;
                        }
                        if ("228999251328368640".equals(player3.getUserID()) && messageToJazz) {
                            player_ = player3;
                            break;
                        }
                    }

                    Whisper.sendWhisper(activeGame, player, player_, messageContent, "n", event.getChannel(),
                            event.getGuild());
                } else if (messageToMyself) {
                    String previousThoughts = "";
                    if (!activeGame.getFactionsThatReactedToThis("futureMessageFor" + player.getFaction()).isEmpty()) {
                        previousThoughts = activeGame
                                .getFactionsThatReactedToThis("futureMessageFor" + player.getFaction()) + ". ";
                    }
                    activeGame.setCurrentReacts("futureMessageFor" + player.getFaction(),
                            previousThoughts + messageContent.replace(":", "666fin"));
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            ButtonHelper.getIdent(player) + " sent themselves a future message");
                } else {
                    String factionColor = StringUtils.substringBefore(messageLowerCase, " ").substring(8);
                    factionColor = AliasHandler.resolveFaction(factionColor);
                    for (Player player3 : activeGame.getPlayers().values()) {
                        if (Objects.equals(factionColor, player3.getFaction()) ||
                                Objects.equals(factionColor, player3.getColor())) {
                            player_ = player3;
                            break;
                        }
                        if ("228999251328368640".equals(player3.getUserID()) && messageToJazz) {
                            player_ = player3;
                            break;
                        }
                    }
                    activeGame.setCurrentReacts("futureMessageFor_" + player_.getFaction() + "_" + player.getFaction(),
                            activeGame.getFactionsThatReactedToThis(
                                    "futureMessageFor_" + player_.getFaction() + "_" + player.getFaction()) + " "
                                    + messageContent.replace(":", "666fin"));
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            ButtonHelper.getIdent(player) + " sent someone else a future message");
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
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(),
                        event.getChannel().asTextChannel().getName(), event.getMember().getEffectiveName(),
                        event.getMessage().getContentDisplay());
                System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getId(),
                        event.getChannel().asTextChannel().getId(), event.getAuthor().getId(),
                        event.getMessage().getContentDisplay());
            }
        }
    }

    public static boolean isAsyncServer(String guildID) {
        for (Guild guild : AsyncTI4DiscordBot.guilds) {
            if (guild.getId().equals(guildID))
                return true;
        }
        return false;
    }
}