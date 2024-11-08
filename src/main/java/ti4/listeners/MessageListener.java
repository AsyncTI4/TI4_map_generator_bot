package ti4.listeners;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.commands.bothelper.CreateGameChannels;
import ti4.commands.fow.Whisper;
import ti4.commands.tech.GetTechButton;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!isAsyncServer(event.getGuild().getId())) {
            return;
        }
        long timeNow = new Date().getTime();
        try {
            Message msg = event.getMessage();
            if (msg.getContentRaw().startsWith("[DELETE]")) {
                msg.delete().queue();
            }
            if (!msg.getAuthor().isBot() && (msg.getContentRaw().contains("boldly go where no stroter has gone before") || msg.getContentRaw().contains("go boldly where no stroter has gone before"))) {
                msg.reply("to explore strange new maps; to seek out new tiles and new factions\nhttps://discord.gg/RZ7qg9kbVZ").queue();
            }
            //947310962485108816
            Role lfgRole = CreateGameChannels.getRole("LFG", event.getGuild());
            if (!event.getAuthor().isBot() && lfgRole != null && event.getChannel() instanceof ThreadChannel && msg.getContentRaw().contains(lfgRole.getAsMention())) {
                String msg2 = lfgRole.getAsMention() + " this game is looking for more members (it's old if it has -launched [FULL] in its title) " + msg.getJumpUrl();
                TextChannel lfgPings = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("lfg-pings", true).stream().findFirst().orElse(null);
                MessageHelper.sendMessageToChannel(lfgPings, msg2);
            }
            if (event.getChannel() instanceof ThreadChannel channel) {
                if (channel.getParentChannel().getName().equalsIgnoreCase("making-new-games")) {
                    Game mapreference = GameManager.getInstance().getGame("finreference");
                    if (mapreference.getStoredValue("makingGamePost" + channel.getId()).isEmpty()) {
                        mapreference.setStoredValue("makingGamePost" + channel.getId(), new Date().getTime() + "");
                        MessageHelper.sendMessageToChannel(event.getChannel(), "To launch a new game, please run the command `/game create_game_button`, filling in the players and fun game name. This will create a button that you may press to launch the game after confirming the members are correct.");
                    }
                }
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
            if (!attachments.isEmpty() && "json".equalsIgnoreCase(attachments.getFirst().getFileExtension())) { // write to
                                                                                                            // file
                String currentDateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmmss"));
                String fileName = "ttpgexport_" + currentDateTime + ".json";
                String filePath = Storage.getTTPGExportDirectory() + "/" + fileName;
                File file = new File(filePath);
                CompletableFuture<File> future = attachments.getFirst().getProxy().downloadToFile(file);
                future.exceptionally(error -> { // handle possible errors
                    error.printStackTrace();
                    return null;
                });
                MessageHelper.sendMessageToChannel(event.getChannel(), "File imported as: `" + fileName + "`");
            }
        }
    }

    public static void autoPingGames() {
        Game mapreference = GameManager.getInstance().getGame("finreference");
        if (mapreference == null) return;
        int multiplier = 1000; // should be 1000
        int tenMin = 10 * 60 * multiplier; // 10 minutes
        long timeSinceLast = (new Date().getTime()) - mapreference.getLastTimeGamesChecked().getTime();

        if (timeSinceLast > tenMin) {
            mapreference.setLastTimeGamesChecked(new Date());
            List<String> storedValues = new ArrayList<>(mapreference.getMessagesThatICheckedForAllReacts().keySet());
            for (String value : storedValues) {
                if (value.startsWith("gameCreator")) {
                    mapreference.removeStoredValue(value);
                }
            }
            GameSaveLoadManager.saveGame(mapreference, "Auto Ping");
            Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();

            for (Game game : mapList.values()) {
                if (!game.isHasEnded()) {
                    Helper.checkAllSaboWindows(game);
                } else {
                    continue;
                }
                if (game.isFastSCFollowMode()) {
                    for (Player player : game.getRealPlayers()) {
                        for (int sc : game.getPlayedSCsInOrder(player)) {
                            if (!player.hasFollowedSC(sc)) {
                                int twenty4 = 24;
                                int half = 12;
                                if (!game.getStoredValue("fastSCFollow").isEmpty()) {
                                    twenty4 = Integer.parseInt(game.getStoredValue("fastSCFollow"));
                                    half = twenty4 / 2;
                                }
                                long twelveHrs = (long) half * 60 * 60 * multiplier;
                                long twentyFourhrs = (long) twenty4 * 60 * 60 * multiplier;
                                String scTime = game.getStoredValue("scPlayMsgTime" + game.getRound() + sc);
                                if (!scTime.isEmpty()) {
                                    long scPlayTime = Long.parseLong(scTime);
                                    long timeDifference = (new Date().getTime()) - scPlayTime;
                                    String timesPinged = game
                                        .getStoredValue("scPlayPingCount" + sc + player.getFaction());
                                    if (timeDifference > twelveHrs && timeDifference < twentyFourhrs) {

                                        if (!timesPinged.equalsIgnoreCase("1")) {
                                            StringBuilder sb = new StringBuilder();
                                            Player p2 = player;
                                            sb.append(p2.getRepresentationUnfogged());
                                            sb.append(" You are getting this ping because ").append(Helper.getSCName(sc, game)).append(" has been played and now it has been half the alloted time and you haven't reacted. Please do so, or after another half you will be marked as not following.");
                                            if (!game.getStoredValue("scPlay" + sc).isEmpty()) {
                                                sb.append("Message link is: ").append(game.getStoredValue("scPlay" + sc)).append("\n");
                                            }
                                            sb.append("You currently have ").append(p2.getStrategicCC())
                                                .append(" CC in your strategy pool.");
                                            if (!p2.hasFollowedSC(sc)) {
                                                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(),
                                                    sb.toString());
                                            }
                                            game.setStoredValue("scPlayPingCount" + sc + player.getFaction(),
                                                "1");
                                        }
                                    }
                                    if (timeDifference > twentyFourhrs) {
                                        if (!timesPinged.equalsIgnoreCase("2")) {
                                            Player p2 = player;
                                            String sb = p2.getRepresentationUnfogged() +
                                                Helper.getSCName(sc, game) + " has been played and now it has been the allotted time and they haven't reacted, so they have been marked as not following.\n";

                                            //MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), sb.toString());
                                            ButtonHelper.sendMessageToRightStratThread(player, game, sb, ButtonHelper.getStratName(sc));
                                            player.addFollowedSC(sc);
                                            game.setStoredValue("scPlayPingCount" + sc + player.getFaction(),
                                                "2");
                                            String messageID = game
                                                .getStoredValue("scPlayMsgID" + sc);
                                            ButtonHelper.addReaction(player, false, true, "Not following", "",
                                                messageID, game);

                                            StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
                                            if (scModel != null && scModel.usesAutomationForSCID("pok8imperial")) {
                                                String key = "factionsThatAreNotDiscardingSOs";
                                                String key2 = "queueToDrawSOs";
                                                String key3 = "potentialBlockers";
                                                if (game.getStoredValue(key2)
                                                    .contains(player.getFaction() + "*")) {
                                                    game.setStoredValue(key2,
                                                        game.getStoredValue(key2)
                                                            .replace(player.getFaction() + "*", ""));
                                                }
                                                if (!game.getStoredValue(key)
                                                    .contains(player.getFaction() + "*")) {
                                                    game.setStoredValue(key,
                                                        game.getStoredValue(key)
                                                            + player.getFaction() + "*");
                                                }
                                                if (game.getStoredValue(key3)
                                                    .contains(player.getFaction() + "*")) {
                                                    game.setStoredValue(key3,
                                                        game.getStoredValue(key3)
                                                            .replace(player.getFaction() + "*", ""));
                                                    Helper.resolveQueue(game);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                long spacer = game.getAutoPingSpacer();
                String playerID = game.getActivePlayerID();
                Player player = null;
                if (playerID != null) {
                    player = game.getPlayer(playerID);

                    if (player != null && player.getPersonalPingInterval() > 0) {
                        spacer = player.getPersonalPingInterval();
                    }
                }
                if ("agendawaiting".equalsIgnoreCase(game.getPhaseOfGame()) && spacer != 0) {
                    spacer = spacer / 3;
                    spacer = Math.max(spacer, 1);
                }
                String key2 = "TechForRound" + game.getRound() + "Counter";
                if (!game.getStoredValue(key2).isEmpty() && !game.getStoredValue(key2).equalsIgnoreCase("0")) {
                    game.setStoredValue(key2, (Integer.parseInt(game.getStoredValue(key2)) - 1) + "");
                    if (game.getStoredValue(key2).equalsIgnoreCase("0")) {
                        GetTechButton.postTechSummary(game);
                    }
                }
                if (game.getAutoPingStatus() && spacer != 0 && !game.isTemporaryPingDisable()) {
                    if ((playerID != null && player != null && !player.isAFK()) || "agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
                        if (player != null || "agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
                            long milliSinceLastPing = new Date().getTime()
                                - game.getLastActivePlayerPing().getTime();
                            if (milliSinceLastPing > (60 * 60 * multiplier * spacer)
                                || (player != null && player.shouldPlayerBeTenMinReminded()
                                    && milliSinceLastPing > (60 * 5 * multiplier))) {
                                String realIdentity = null;
                                String ping = null;
                                if (player != null) {
                                    realIdentity = player.getRepresentationUnfogged();
                                    ping = realIdentity + " this is a gentle reminder that it is your turn.";
                                    if (player != null && player.shouldPlayerBeTenMinReminded()
                                        && milliSinceLastPing > (60 * 5 * multiplier) && (60 * 60 * multiplier * spacer) > milliSinceLastPing) {
                                        ping = realIdentity + " this is a quick nudge in case you forgot to end turn. Please forgive the impertinance";
                                    }
                                    String playersInCombat = game.getStoredValue("factionsInCombat");
                                    if (!playersInCombat.isBlank() && playersInCombat.contains(player.getFaction())) {
                                        for (Player p2 : game.getRealPlayers()) {
                                            if (p2 == player) {
                                                continue;
                                            }
                                            if (playersInCombat.contains(p2.getFaction())) {
                                                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation() + " the bot thinks you might be in combat and should receive a reminder ping as well. Ignore if not relevant");
                                            }
                                        }
                                    }
                                }
                                if ("agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
                                    AgendaHelper.pingMissingPlayers(game);
                                } else {
                                    long milliSinceLastTurnChange = new Date().getTime()
                                        - game.getLastActivePlayerChange().getTime();
                                    int autoPingSpacer = (int) spacer;
                                    int pingNumber = (int) (milliSinceLastTurnChange
                                        / (60 * 60 * multiplier * autoPingSpacer));
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 2)) {
                                        ping = realIdentity
                                            + " this is a courtesy notice that the game is waiting (impatiently).";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 3)) {
                                        ping = realIdentity
                                            + " this is a brusk missive stating that while you may sleep, the bot never does (and it's been told to ping you about it).";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 4)) {
                                        ping = realIdentity
                                            + " this is a sternly worded letter from the bot regarding your noted absence.";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 5)) {
                                        ping = realIdentity
                                            + " this is a firm request from the bot that you do something to end this situation.";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * 6)) {
                                        ping = realIdentity
                                            + " Half dozen times the charm they say.";
                                    }
                                    if (pingNumber == 7) {
                                        ping = realIdentity
                                            + " I may write whatever I want here, not like you've checked in to read any of it anyways.";
                                    }
                                    if (pingNumber == 8) {
                                        ping = realIdentity
                                            + " You should end turn soon, there might be a bear on the loose, and you know which friend gets eaten by the bear.";
                                    }
                                    if (pingNumber == 9) {
                                        ping = realIdentity
                                            + " There's a rumor going around that some game is looking for a replacement player. Not that the bot would know anything about that (who are we kidding, the bot knows everything, it just acts dumb sometimes to fool you into a state of compliance).";
                                    }
                                    if (pingNumber == 10) {
                                        ping = realIdentity
                                            + " Do you ever wonder what we're doing here? Such a short time here on earth, and here we are, spending some of it waiting for a TI4 game to move. Well, at least some of us probably are.";
                                    }
                                    if (pingNumber == 11) {
                                        ping = realIdentity
                                            + " We should hire some monkeys to write these prompts. Then at least these reminders would be productive and maybe one day produce Shakespeare.";
                                    }
                                    if (pingNumber == 12) {
                                        ping = realIdentity
                                            + " This is lucky number 12. You wanna move now to avoid the bad luck of 13. Don't say we didn't warn you.";
                                    }
                                    if (pingNumber == 13) {
                                        ping = realIdentity
                                            + " All your troops decided it was holiday leave and they went home. Good luck getting them back into combat readiness by the time you need them.";
                                    }
                                    if (pingNumber == 14) {
                                        ping = realIdentity
                                            + " The turtles who bear the weight of the universe are going to die from old-age soon. Better pick up the pace or the game will never finish.";
                                    }
                                    if (pingNumber == 15) {
                                        ping = realIdentity
                                            + " The turtles who bear the weight of the universe are going to die from old-age soon. Better pick up the pace or the game will never finish.";
                                    }
                                    if (pingNumber == 17) {
                                        ping = realIdentity
                                            + " Your name is gonna be put on the bot's top 10 most wanted players soon. There's currently 27 players on that list, you don't wanna join em.";
                                    }
                                    if (pingNumber == 16) {
                                        ping = realIdentity
                                            + " You thought the duplicate ping before meant that the bot had run out of things to say about how boring it is to wait this long. Shows how much you know.";
                                    }
                                    if (pingNumber == 18) {
                                        ping = realIdentity
                                            + " The bot's decided to start training itself to take over your turn. At its current rate of development, you have -212 days until it knows the rules better than you.";
                                    }
                                    if (pingNumber == 19) {
                                        ping = realIdentity
                                            + " They say nice guys finish last, but clearly they haven't seen your track record.";
                                    }
                                    if (pingNumber == 20) {
                                        ping = realIdentity
                                            + " Wait too much longer, and the bot is gonna hire some Vuil'raith hit-cultists to start rifting your ships.";
                                    }
                                    if (pingNumber == 21) {
                                        ping = realIdentity
                                            + " Supposedly great things come to those who wait. If that's true, you owe me something roughly the size of Mount Everest.";
                                    }
                                    if (pingNumber == 22) {
                                        ping = realIdentity + " Knock knock.";
                                    }
                                    if (pingNumber == 23) {
                                        ping = realIdentity + " Who's there?";
                                    }
                                    if (pingNumber == 24) {
                                        ping = realIdentity + " It sure ain't you.";
                                    }
                                    if (pingNumber == 25) {
                                        ping = realIdentity
                                            + " I apologize, we bots don't have much of a sense of humor, but who knows, maybe you would have laughed if you were here ;_;";
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
                                            + " It's been ages, when will I get a chance to ping someone else in this game? Don't you want them to feel needed too?";
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
                                            + " When it started, I dreamed that this game was going to be a great one, full of exciting battles to record in the chronicles. Instead it looks doomed to the waste-bin, unceremoniously ended a few weeks from now. I guess most dreams end that way.";
                                    }
                                    if (pingNumber == 32) {
                                        ping = realIdentity
                                            + " Did I ever tell you about my Uncle Fred? He went missing once too. We eventually found him, cooped up in a some fog game, continuously pinging a player who wasn't there. Not a good way for a bot to go.";
                                    }
                                    if (pingNumber == 33) {
                                        ping = realIdentity + " To-morrow, and to-morrow, and to-morrow,\n" + //
                                            "Creeps in this petty pace from day to day,\n" + //
                                            "To the last syllable of recorded time;\n" + //
                                            "And all our yesterdays have lighted fools\n" + //
                                            "The way to dusty death. Out, out, brief candle!\n" + //
                                            "Life's but a walking shadow.";
                                    }
                                    if (pingNumber == 34) {
                                        ping = realIdentity
                                            + " Perhaps you're torn by indecision. Just remember what my grandma always used to say: When in doubt, go for the throat.";
                                    }
                                    if (pingNumber == 35) {
                                        ping = realIdentity + " Life's but a walking shadow, a poor player\n" +
                                            "That struts and frets his hour upon the stage\n" +
                                            "And then is heard no more. It is a tale\n" +
                                            "Told by an idiot, full of sound and fury\n" +
                                            "Signifying nothing.";
                                    }
                                    if (pingNumber == 36) {
                                        ping = realIdentity
                                            + " Life may not signify anything, but these pings signify that you should take your turn! This is your hour upon the stage, and the audience won't wait forever!";
                                    }
                                    if (pingNumber == 37) {
                                        ping = realIdentity
                                            + " I think you're supposed to forgive your enemies 7 times 70 times. Since I consider you only a mild acquaintance, I'll give you 2 times 20 times.";
                                    }
                                    if (pingNumber == 38) {
                                        ping = realIdentity + " I assure you that the winning move here is TO PLAY.";
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
                                            + " ||Can I do spoiler tag pings? Guess you'll never know.||";
                                    }
                                    if (pingNumber == 42) {
                                        ping = realIdentity
                                            + " They say money can't buy happiness, but I hear that trade goods may buy a war sun, which is basically the same thing.";
                                    }

                                    int maxSoFar = 42;
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * maxSoFar)) {
                                        ping = realIdentity
                                            + " Rumors of the bot running out of stamina are greatly exaggerated. The bot will win this stare-down, it is simply a matter of time.";
                                    }
                                    if (milliSinceLastTurnChange > (60 * 60 * multiplier * spacer * (maxSoFar + 3)) && !game.isFowMode()) {
                                        ping = realIdentity
                                            + " this is your final reminder. Stopping pinging now so we don't come back in 2 months and find 600+ messages.";
                                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                                            game.getPing()
                                                + " the game has stalled on a player, and autoping will now stop pinging them.");
                                        game.setTemporaryPingDisable(true);
                                    } else {
                                        if (game.isFowMode()) {
                                            MessageHelper.sendPrivateMessageToPlayer(player, game, ping);
                                            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                                                "Active player has been pinged. This is ping #" + pingNumber);
                                        } else {
                                            MessageChannel gameChannel = player.getCorrectChannel();
                                            if (gameChannel != null) {
                                                MessageHelper.sendMessageToChannel(gameChannel, ping);
                                                if (ping != null && ping.contains("courtesy notice")) {
                                                    List<Button> buttons = new ArrayList<>();
                                                    buttons.add(Buttons.red("temporaryPingDisable",
                                                        "Disable Pings For Turn"));
                                                    buttons.add(Buttons.gray("deleteButtons", "Delete These Buttons"));
                                                    MessageHelper.sendMessageToChannelWithButtons(gameChannel, realIdentity
                                                        + " if the game is not waiting on you, you may disable the auto ping for this turn so it doesn't annoy you. It will turn back on for the next turn.",
                                                        buttons);
                                                }
                                            }
                                        }
                                    }
                                    if (player != null)
                                        ButtonHelper.increasePingCounter(mapreference, player.getUserID());
                                }
                                if (player != null) {
                                    player.setWhetherPlayerShouldBeTenMinReminded(false);
                                }
                                game.setLastActivePlayerPing(new Date());
                                GameSaveLoadManager.saveGame(game, "Auto Ping");
                            }
                        }
                    } else {
                        long milliSinceLastPing = new Date().getTime() - game.getLastActivePlayerPing().getTime();
                        if (milliSinceLastPing > (60 * 60 * multiplier * game.getAutoPingSpacer())) {
                            if ("agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
                                AgendaHelper.pingMissingPlayers(game);
                                game.setLastActivePlayerPing(new Date());
                                GameSaveLoadManager.saveGame(game, "Auto Ping");
                            }
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
        // Game game = GameManager.getInstance().getGame(gameName);
        // if(game != null && game.getPublicObjectives1() != null &&
        // game.getPublicObjectives1().size() > 1 && game.getBotShushing()){
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

            Game game = GameManager.getInstance().getGame(gameName);
            if (game != null && game.isBotFactionReacts() && !game.isFowMode()) {
                Player player = game.getPlayer(event.getAuthor().getId());
                if (game.isCommunityMode()) {

                    List<Role> roles = event.getMember().getRoles();
                    for (Player player2 : game.getRealPlayers()) {
                        if (roles.contains(player2.getRoleForCommunity())) {
                            player = player2;
                        }
                        if (player2.getTeamMateIDs().contains(event.getMember().getUser().getId())) {
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

        List<String> colors = Mapper.getColorNames();
        colors.addAll(Mapper.getFactionIDs());
        String messageText = msg.getContentRaw();
        String messageLowerCase = messageText.toLowerCase();
        boolean messageToColor = false;
        boolean messageToFutureColor = false;
        boolean messageToMyself = false;
        boolean messageToJazz = false;
        boolean endOfRoundSummary = false;
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
        if (messageLowerCase.startsWith("endofround")) {
            endOfRoundSummary = true;
        }
        if (messageLowerCase.startsWith("tojazz") || messageLowerCase.startsWith("tofuturejazz")) {
            messageToJazz = true;
        }

        // FoW - replicate messages in combat threads so that observers can see
        boolean isFowCombatThread = event.getChannel() instanceof ThreadChannel
            && event.getChannel().getName().contains("vs")
            && event.getChannel().getName().contains("private");
        if (isFowCombatThread) {
            String gameName2 = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));

            Game game = GameManager.getInstance().getGame(gameName2);
            Player player3 = game.getPlayer(event.getAuthor().getId());
            if (game.isCommunityMode()) {
                Collection<Player> players = game.getPlayers().values();
                List<Role> roles = event.getMember().getRoles();
                for (Player player2 : players) {
                    if (roles.contains(player2.getRoleForCommunity())) {
                        player3 = player2;
                    }

                }
            }

            if (game.isFowMode() &&
                ((player3 != null && player3.isRealPlayer()
                    && event.getChannel().getName().contains(player3.getColor()) && !event.getAuthor().isBot())
                    || (event.getAuthor().isBot() && messageText.contains("Total hits ")))) {

                String systemPos;
                if (StringUtils.countMatches(event.getChannel().getName(), "-") > 4) {
                    systemPos = event.getChannel().getName().split("-")[4];
                } else {
                    return;
                }
                Tile tile = game.getTileByPosition(systemPos);
                for (Player player : game.getRealPlayers()) {
                    if (player3 != null && player == player3) {
                        continue;
                    }
                    if (!tile.getRepresentationForButtons(game, player).contains("(")) {
                        continue;
                    }
                    MessageChannel pChannel = player.getPrivateChannel();
                    TextChannel pChan = (TextChannel) pChannel;
                    if (pChan != null) {
                        String threadName = event.getChannel().getName();
                        boolean combatParticipant = threadName.contains("-" + player.getColor() + "-");
                        String newMessage = player.getRepresentation(true, combatParticipant) + " Someone said: " + messageText;
                        if (event.getAuthor().isBot() && messageText.contains("Total hits ")) {
                            String hits = StringUtils.substringAfter(messageText, "Total hits ");
                            String location = StringUtils.substringAfter(messageText, "rolls for ");
                            location = StringUtils.substringBefore(location, " Combat");
                            newMessage = player.getRepresentation(true, combatParticipant) + " Someone rolled dice for " + location
                                + " and got a total of **" + hits + " hit" + (hits.equals("1") ? "" : "s");
                        }
                        if (!event.getAuthor().isBot() && player3 != null && player3.isRealPlayer()) {
                            newMessage = player.getRepresentation(true, combatParticipant) + " "
                                + StringUtils.capitalize(player3.getColor()) + " said: " + messageText;
                        }

                        newMessage = newMessage.replace("Total hits", "");
                        List<ThreadChannel> threadChannels = pChan.getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().contains(threadName) && threadChannel_ != event.getChannel()) {
                                MessageHelper.sendMessageToChannel(threadChannel_, newMessage);
                            }
                        }
                    }
                }
            }
        }

        // All Games - send whispers etc
        if (messageToColor || messageToMyself || messageToFutureColor || messageToJazz || endOfRoundSummary) {
            String messageContent = StringUtils.substringAfter(messageText, " ");
            String messageBeginning = StringUtils.substringBefore(messageText, " ");
            String gameName = event.getChannel().getName();
            gameName = gameName.replace("Cards Info-", "");
            gameName = gameName.substring(0, gameName.indexOf("-"));
            Game game = GameManager.getInstance().getGame(gameName);

            if (messageContent.isEmpty()) {
                BotLogger.log("User tried to send an empty whisper " + event.getJumpUrl());
            } else if (game != null) {
                Player player = game.getPlayer(event.getAuthor().getId());
                if (game.isCommunityMode()) {
                    List<Role> roles = event.getMember().getRoles();
                    for (Player player2 : game.getRealPlayers()) {
                        if (roles.contains(player2.getRoleForCommunity())) {
                            player = player2;
                        }
                        if (player2.getTeamMateIDs().contains(event.getMember().getUser().getId())) {
                            player = player2;
                        }
                    }
                }

                Player player_ = game.getPlayer(event.getAuthor().getId());
                if (messageToJazz && game.getRealPlayerIDs().contains(Constants.jazzId)) {
                    if (player_.getUserID().equals(Constants.jazzId)) {
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
                    for (Player player3 : game.getRealPlayers()) {
                        if (Objects.equals(factionColor, player3.getFaction()) ||
                            Objects.equals(factionColor, player3.getColor())) {
                            player_ = player3;
                            break;
                        }
                        if (Constants.jazzId.equals(player3.getUserID()) && messageToJazz) {
                            player_ = player3;
                            break;
                        }
                    }

                    //if no target player was found
                    if (Objects.equals(player, player_)) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found.");
                        return;
                    }
                    Whisper.sendWhisper(game, player, player_, messageContent, "n", event.getChannel(), event.getGuild());
                } else if (messageToMyself) {
                    String previousThoughts = "";
                    if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
                        previousThoughts = game.getStoredValue("futureMessageFor" + player.getFaction()) + "\n\n";
                    }
                    game.setStoredValue("futureMessageFor" + player.getFaction(), previousThoughts + messageContent);
                    MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " sent themselves a future message");
                } else if (endOfRoundSummary) {
                    RoundSummaryHelper.storeEndOfRoundSummary(game, player, messageBeginning, messageContent, true, event.getChannel());
                } else {
                    String factionColor = StringUtils.substringBefore(messageLowerCase, " ").substring(8);
                    factionColor = AliasHandler.resolveFaction(factionColor);
                    for (Player player3 : game.getPlayers().values()) {
                        if (Objects.equals(factionColor, player3.getFaction()) ||
                            Objects.equals(factionColor, player3.getColor())) {
                            player_ = player3;
                            break;
                        }
                        if (Constants.jazzId.equals(player3.getUserID()) && messageToJazz) {
                            player_ = player3;
                            break;
                        }
                    }
                    String futureMsgKey = "futureMessageFor_" + player_.getFaction() + "_" + player.getFaction();
                    game.setStoredValue(futureMsgKey, game.getStoredValue(futureMsgKey) + "\n\n" + messageContent);
                    MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " sent someone else a future message");
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

    private static boolean isAsyncServer(String guildID) {
        for (Guild guild : AsyncTI4DiscordBot.guilds) {
            if (guild.getId().equals(guildID))
                return true;
        }
        return false;
    }
}
