package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.WhisperService;
import ti4.service.game.CreateGameService;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!isAsyncServer(event.getGuild().getId())) {
            return;
        }

        Message message = event.getMessage();
        if (message.getContentRaw().startsWith("[DELETE]")) {
            message.delete().queue();
        }
        AsyncTI4DiscordBot.runAsync("Message listener task", () -> processMessage(event, message));
    }

    private static void processMessage(@Nonnull MessageReceivedEvent event, Message message) {
        try {
            timeIt(() -> checkForFogOfWarInvitePrompt(message), "MessageListener#checkForFogOfWarInvitePrompt", 1000);
            timeIt(() -> copyLFGPingsToLFGPingsChannel(event, message), "MessageListener#copyLFGPingstoLFGPingsChannel", 1000);
            timeIt(() -> checkIfNewMakingGamesPostAndPostIntroduction(event), "MessageListener#checkIfNewMakingGamesPostAndPostIntroduction", 1000);
            timeIt(() -> handleWhispers(event, message), "MessageListener#handleWhispers", 1000);
            timeIt(() -> handleFogOfWarCombatThreadMirroring(event), "MessageListener#handleFogOfWarCombatThreadMirroring", 1000);
            timeIt(() -> endOfRoundSummary(event, message), "MessageListener#endOfRoundSummary", 1000);
            timeIt(() -> addFactionEmojiReactionsToMessages(event), "MessageListener#addFactionEmojiReactionsToMessages", 1000);
        } catch (Exception e) {
            BotLogger.log("`MessageListener.onMessageReceived`   Error trying to handle a received message:\n> " + event.getMessage().getJumpUrl(), e);
        }
    }

    private static boolean isAsyncServer(String guildID) {
        return AsyncTI4DiscordBot.guilds.stream().anyMatch(g -> g.getId().equals(guildID));
    }

    public static void timeIt(Runnable runnable, String methodName, long warnIfLongerThanMillis) {
        long startTime = System.currentTimeMillis();

        runnable.run();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        if (duration > warnIfLongerThanMillis) {
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(duration);
            String errorMessage = "`" + methodName + "` took over " + warnIfLongerThanMillis + "ms to process:\n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(startTime) + " start \n> " +
                DateTimeHelper.getTimestampFromMillesecondsEpoch(endTime) + " end `" + executionTime + "` to execute";
            BotLogger.log(errorMessage);
        }
    }

    private void handleFoWWhispersAndFowCombats(MessageReceivedEvent event, Message msg) {
        if (!event.getAuthor().isBot() && event.getChannel().getName().contains("-")) {
            String gameName = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));

            Game game = GameManager.getGame(gameName);
            if (game != null && game.isBotFactionReacts() && !game.isFowMode()) {
                Player player = getPlayer(event, game);
                try {
                    MessageHistory mHistory = event.getChannel().getHistory();
                    RestAction<List<Message>> lis = mHistory.retrievePast(2);
                    var messages = lis.complete();
                    if (messages.size() == 2 && !event.getMessage().getAuthor().getId().equalsIgnoreCase(messages.get(1).getAuthor().getId()) &&
                        player != null && player.isRealPlayer()) {
                        event.getChannel().addReactionById(event.getMessageId(),
                            Emoji.fromFormatted(player.getFactionEmoji())).queue();
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

            Game game = GameManager.getGame(gameName2);
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
            Game game = GameManager.getGame(gameName);

            if (messageContent.isEmpty()) {
                BotLogger.log("User tried to send an empty whisper " + event.getJumpUrl());
            } else if (game != null) {
                Player player = getPlayer(event, game);
                Player player_ = game.getPlayer(event.getAuthor().getId());
                if (messageToJazz && game.getRealPlayerIDs().contains(Constants.jazzId)) {
                    if (player_.getUserID().equals(Constants.jazzId)) {
                        messageToMyself = true;
                    } else {
                        if (!messageLowerCase.startsWith("tofuture")) {
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
                    WhisperService.sendWhisper(game, player, player_, messageContent, "n", event.getChannel(), event.getGuild());
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

    private static Player getPlayer(MessageReceivedEvent event, Game game) {
        Player player = game.getPlayer(event.getAuthor().getId());
        if (!game.isCommunityMode()) {
            return player;
        }
        List<Role> roles = event.getMember().getRoles();
        for (Player player2 : game.getRealPlayers()) {
            if (roles.contains(player2.getRoleForCommunity())) {
                player = player2;
            }
            if (player2.getTeamMateIDs().contains(event.getMember().getUser().getId())) {
                player = player2;
            }
        }
        return player;
    }

    private static void checkForFogOfWarInvitePrompt(Message message) {
        if (!message.getAuthor().isBot() && (message.getContentRaw().contains("boldly go where no stroter has gone before") || message.getContentRaw().contains("go boldly where no stroter has gone before"))) {
            message.reply("to explore strange new maps; to seek out new tiles and new factions\nhttps://discord.gg/RZ7qg9kbVZ").queue();
        }
    }

    private static void copyLFGPingsToLFGPingsChannel(MessageReceivedEvent event, Message message) {
        //947310962485108816
        Role lfgRole = CreateGameService.getRole("LFG", event.getGuild());
        if (!event.getAuthor().isBot() && lfgRole != null && event.getChannel() instanceof ThreadChannel && message.getContentRaw().contains(lfgRole.getAsMention())) {
            String msg2 = lfgRole.getAsMention() + " this game is looking for more members (it's old if it has -launched [FULL] in its title) " + message.getJumpUrl();
            TextChannel lfgPings = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("lfg-pings", true).stream().findFirst().orElse(null);
            MessageHelper.sendMessageToChannel(lfgPings, msg2);
        }
    }

    private static void checkIfNewMakingGamesPostAndPostIntroduction(MessageReceivedEvent event) {
        if (!(event.getChannel() instanceof ThreadChannel channel) || !channel.getParentChannel().getName().equalsIgnoreCase("making-new-games")) {
            return;
        }
        Game mapreference = GameManager.getGame("finreference");
        if (mapreference.getStoredValue("makingGamePost" + channel.getId()).isEmpty()) {
            mapreference.setStoredValue("makingGamePost" + channel.getId(), System.currentTimeMillis() + "");
            MessageHelper.sendMessageToChannel(event.getChannel(), "To launch a new game, please run the command `/game create_game_button`, filling in the players and fun game name. This will create a button that you may press to launch the game after confirming the members are correct.");
            GameSaveLoadManager.saveGame(mapreference, "newChannel");
        }
    }

    private static void endOfRoundSummary(MessageReceivedEvent event, Message msg) {
        if (!msg.getContentRaw().toLowerCase().startsWith("endofround")) {
            return;
        }
        String gameName = event.getChannel().getName();
        gameName = gameName.replace("Cards Info-", "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Game game = GameManager.getGame(gameName);

        String messageText = msg.getContentRaw();
        String messageBeginning = StringUtils.substringBefore(messageText, " ");
        String messageContent = StringUtils.substringAfter(messageText, " ");
        if (game != null) {
            Player player = getPlayer(event, game);
            RoundSummaryHelper.storeEndOfRoundSummary(game, player, messageBeginning, messageContent, true, event.getChannel());
        }
    }

    private static void handleWhispers(MessageReceivedEvent event, Message msg) {
        if (msg.getContentRaw().contains("used /fow whisper")) {
            msg.delete().queue();
        }

        String messageText = msg.getContentRaw();
        if (!messageText.toLowerCase().startsWith("to") || !messageText.contains(" ")) {
            return;
        }

        String messageLowerCase = messageText.toLowerCase();
        if (messageLowerCase.startsWith("tofutureme")) {
            whisperToFutureMe(event);
            return;
        }

        String whoIsItTo = StringUtils.substringBetween(messageLowerCase, "to", " ");
        boolean future = whoIsItTo.startsWith("future");
        whoIsItTo = whoIsItTo.replaceFirst("future", "");
        if (whoIsItTo.isEmpty()) {
            return;
        }

        if (!Mapper.isValidColor(whoIsItTo) && !Mapper.isValidFaction(AliasHandler.resolveFaction(whoIsItTo))) {
            return;
        }

        String gameName = event.getChannel().getName();
        gameName = gameName.replace("Cards Info-", "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Game game = GameManager.getGame(gameName);

        if (game == null) {
            return;
        }

        String messageContent = StringUtils.substringAfter(messageText, " ");
        if (messageContent.isEmpty()) {
            msg.reply("No message content?").queue();
            return;
        }

        Player player = getPlayer(event, game);
        Player player_ = game.getPlayer(event.getAuthor().getId());

        if (future) {
            whisperToFutureColorOrFaction(event, whoIsItTo, game, messageContent, player, player_);
        } else {
            whoIsItTo = AliasHandler.resolveFaction(whoIsItTo);
            for (Player player3 : game.getRealPlayers()) {
                if (Objects.equals(whoIsItTo, player3.getFaction()) ||
                    Objects.equals(whoIsItTo, player3.getColor())) {
                    player_ = player3;
                    break;
                }
            }

            //if no target player was found
            if (Objects.equals(player, player_)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found: " + whoIsItTo);
                return;
            }
            WhisperService.sendWhisper(game, player, player_, messageContent, "n", event.getChannel(), event.getGuild());
            msg.delete().queue();
        }
    }

    private static void whisperToFutureColorOrFaction(MessageReceivedEvent event, String whoIsItTo, Game game, String messageContent, Player player, Player player_) {
        String factionColor = whoIsItTo;
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player3 : game.getPlayers().values()) {
            if (Objects.equals(factionColor, player3.getFaction()) ||
                Objects.equals(factionColor, player3.getColor())) {
                player_ = player3;
                break;
            }
        }
        String futureMsgKey = "futureMessageFor_" + player_.getFaction() + "_" + player.getFaction();
        game.setStoredValue(futureMsgKey, game.getStoredValue(futureMsgKey) + "\n\n" + messageContent);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " sent someone else a future message");
        event.getMessage().delete().queue();
    }

    private static void whisperToFutureMe(MessageReceivedEvent event) {
        String gameName = event.getChannel().getName();
        gameName = gameName.replace("Cards Info-", "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Game game = GameManager.getGame(gameName);
        String messageContent = StringUtils.substringAfter(event.getMessage().getContentRaw(), " ");
        Player player = getPlayer(event, game);

        String previousThoughts = "";
        if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            previousThoughts = game.getStoredValue("futureMessageFor" + player.getFaction()) + "\n\n";
        }
        game.setStoredValue("futureMessageFor" + player.getFaction(), previousThoughts + messageContent);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " sent themselves a future message");
        event.getMessage().delete().queue();
    }

    private static void addFactionEmojiReactionsToMessages(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.getChannel().getName().contains("-")) {
            return;
        }
        String gameName = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));
        Game game = GameManager.getGame(gameName);
        if (game == null || !game.isBotFactionReacts() || game.isFowMode()) {
            return;
        }
        Player player = getPlayer(event, game);
        if (player == null || !player.isRealPlayer()) {
            return;
        }
        try {
            event.getChannel().getHistory()
                .retrievePast(2)
                .queue(messages -> {
                    if (messages.size() == 2 && !event.getMessage().getAuthor().getId().equalsIgnoreCase(messages.get(1).getAuthor().getId())) {
                        var emoji = Emoji.fromFormatted(player.getFactionEmoji());
                        messages.getFirst().addReaction(emoji).queue();
                    }
                });
        } catch (Exception e) {
            BotLogger.log("Reading previous message", e);
        }
    }

    /**
     * replicate messages in combat threads so that observers can see
     */
    private static void handleFogOfWarCombatThreadMirroring(MessageReceivedEvent event) {
        // Don't execute if: 
        if (AsyncTI4DiscordBot.guildFogOfWar != null && // fog server exists
            !AsyncTI4DiscordBot.guildFogOfWar.getId().equals(event.getGuild().getId()) && // event server IS NOT the fog server
            AsyncTI4DiscordBot.guildPrimaryID.equals(Constants.ASYNCTI4_HUB_SERVER_ID)) // bot is running in production
        {
            return;
        } // else it's probably a dev/test server, so execute

        String messageText = event.getMessage().getContentRaw();
        boolean isFowCombatThread = event.getChannel() instanceof ThreadChannel
            && event.getChannel().getName().contains("vs")
            && event.getChannel().getName().contains("private");
        if (!isFowCombatThread) {
            return;
        }
        String gameName = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));

        Game game = GameManager.getGame(gameName);
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
}
