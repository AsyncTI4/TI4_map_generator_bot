package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.WhisperService;
import ti4.service.game.CreateGameService;
import ti4.service.game.GameNameService;

public class MessageListener extends ListenerAdapter {

    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 1;

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands() || !AsyncTI4DiscordBot.isValidGuild(event.getGuild().getId())) {
            return;
        }

        Message message = event.getMessage();
        if (message.getContentRaw().startsWith("[DELETE]")) {
            message.delete().queue();
            return;
        }

        AsyncTI4DiscordBot.runAsync("Message listener task", EXECUTION_TIME_WARNING_THRESHOLD_SECONDS, () -> processMessage(event, message));
    }

    private static void processMessage(@Nonnull MessageReceivedEvent event, Message message) {
        try {
            if (!event.getAuthor().isBot()) {
                if (handleWhispers(event, message)) return;
                if (endOfRoundSummary(event, message)) return;
                if (addFactionEmojiReactionsToMessages(event)) return;
                if (checkForFogOfWarInvitePrompt(message)) return;
                if (copyLFGPingsToLFGPingsChannel(event, message)) return;
            }
            handleFogOfWarCombatThreadMirroring(event);
        } catch (Exception e) {
            BotLogger.log("`MessageListener.onMessageReceived`   Error trying to handle a received message:\n> " +
                event.getMessage().getJumpUrl(), e);
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
                return player2;
            }
            if (player2.getTeamMateIDs().contains(event.getMember().getUser().getId())) {
                return player2;
            }
        }
        return player;
    }

    private static boolean checkForFogOfWarInvitePrompt(Message message) {
        if (!message.getContentRaw().contains("boldly go where no stroter has gone before") &&
                !message.getContentRaw().contains("go boldly where no stroter has gone before")) {
            return false;
        }
        message.reply("to explore strange new maps; to seek out new tiles and new factions\nhttps://discord.gg/RZ7qg9kbVZ").queue();
        return true;
    }

    private static boolean copyLFGPingsToLFGPingsChannel(MessageReceivedEvent event, Message message) {
        if (!(event.getChannel() instanceof ThreadChannel)) {
            return false;
        }
        Role lfgRole = CreateGameService.getRole("LFG", event.getGuild()); //947310962485108816
        if (lfgRole == null || !message.getContentRaw().contains(lfgRole.getAsMention())) {
            return false;
        }
        String msg2 = lfgRole.getAsMention() + " this game is looking for more members (it's old if it has -launched [FULL] in its title) " + message.getJumpUrl();
        TextChannel lfgPings = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("lfg-pings", true).stream().findFirst().orElse(null);
        MessageHelper.sendMessageToChannel(lfgPings, msg2);
        return true;
    }

    private static boolean endOfRoundSummary(MessageReceivedEvent event, Message message) {
        if (!message.getContentRaw().toLowerCase().startsWith("endofround")) {
            return false;
        }
        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        Game game = GameManager.getManagedGame(gameName).getGame();

        String messageText = message.getContentRaw();
        String messageBeginning = StringUtils.substringBefore(messageText, " ");
        String messageContent = StringUtils.substringAfter(messageText, " ");
        if (game != null) {
            Player player = getPlayer(event, game);
            RoundSummaryHelper.storeEndOfRoundSummary(game, player, messageBeginning, messageContent, true, event.getChannel());
            GameManager.save(game, "End of round summary.");
        }
        return true;
    }

    private static boolean handleWhispers(MessageReceivedEvent event, Message message) {
        if (message.getContentRaw().contains("used /fow whisper")) {
            message.delete().queue();
        }

        String messageText = message.getContentRaw();
        if (!messageText.toLowerCase().startsWith("to") || !messageText.contains(" ")) {
            return false;
        }

        String messageLowerCase = messageText.toLowerCase();
        if (messageLowerCase.startsWith("tofutureme")) {
            whisperToFutureMe(event);
            return false;
        }

        String receivingColorOrFaction = StringUtils.substringBetween(messageLowerCase, "to", " ");
        boolean future = receivingColorOrFaction.startsWith("future");
        receivingColorOrFaction = receivingColorOrFaction.replaceFirst("future", "");
        if (receivingColorOrFaction.isEmpty()) {
            return false;
        }

        receivingColorOrFaction = AliasHandler.resolveFaction(receivingColorOrFaction);
        if (!Mapper.isValidColor(receivingColorOrFaction) && !Mapper.isValidFaction(receivingColorOrFaction)) {
            return false;
        }

        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        Game game = GameManager.getManagedGame(gameName).getGame();

        if (game == null) {
            return false;
        }

        String messageContent = StringUtils.substringAfter(messageText, " ");
        if (messageContent.isEmpty()) {
            message.reply("No message content?").queue();
            return false;
        }

        Player sender = getPlayer(event, game);
        Player receiver = null;

        for (Player player : game.getRealPlayers()) {
            if (Objects.equals(receivingColorOrFaction, player.getFaction()) || Objects.equals(receivingColorOrFaction, player.getColor())) {
                receiver = player;
                break;
            }
        }

        if (receiver == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found: " + receivingColorOrFaction);
            return false;
        }

        if (future) {
            whisperToFutureColorOrFaction(event, game, messageContent, sender, receiver);
        } else {
            WhisperService.sendWhisper(game, sender, receiver, messageContent, "n", event.getChannel(), event.getGuild());
            message.delete().queue();
        }
        return true;
    }

    private static void whisperToFutureColorOrFaction(MessageReceivedEvent event, Game game, String messageContent, Player sender, Player receiver) {
        String futureMsgKey = "futureMessageFor_" + receiver.getFaction() + "_" + sender.getFaction();
        game.setStoredValue(futureMsgKey, game.getStoredValue(futureMsgKey) + "\n\n" + messageContent);
        MessageHelper.sendMessageToChannel(event.getChannel(), sender.getFactionEmoji() + " sent someone else a future message");
        event.getMessage().delete().queue();
    }

    private static void whisperToFutureMe(MessageReceivedEvent event) {
        String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
        Game game = GameManager.getManagedGame(gameName).getGame();
        String messageContent = StringUtils.substringAfter(event.getMessage().getContentRaw(), " ");
        Player player = getPlayer(event, game);

        String previousThoughts = "";
        if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            previousThoughts = game.getStoredValue("futureMessageFor" + player.getFaction()) + "\n\n";
        }
        game.setStoredValue("futureMessageFor" + player.getFaction(), previousThoughts + messageContent);
        GameManager.save(game, "Whisper to future.");
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " sent themselves a future message");
        event.getMessage().delete().queue();
    }

    private static boolean addFactionEmojiReactionsToMessages(MessageReceivedEvent event) {
        Channel channel = event.getChannel();
        if (!channel.getName().contains("-")) {
            return false;
        }
        String gameName = GameNameService.getGameNameFromChannel(channel);
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null || !managedGame.isFactionReactMode() || managedGame.isFowMode()) {
            return false;
        }
        Player player = getPlayer(event, managedGame.getGame());
        if (player == null || !player.isRealPlayer()) {
            return false;
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
        return true;
    }

    /**
     * replicate messages in combat threads so that observers can see
     */
    private static boolean handleFogOfWarCombatThreadMirroring(MessageReceivedEvent event) {
        if (AsyncTI4DiscordBot.guildFogOfWar != null && // fog server exists
            !AsyncTI4DiscordBot.guildFogOfWar.getId().equals(event.getGuild().getId()) && // event server IS NOT the fog server
            AsyncTI4DiscordBot.guildPrimaryID.equals(Constants.ASYNCTI4_HUB_SERVER_ID)) {// bot is running in production
            return false;
        } // else it's probably a dev/test server, so execute

        String messageText = event.getMessage().getContentRaw();
        boolean isFowCombatThread = event.getChannel() instanceof ThreadChannel
            && event.getChannel().getName().contains("vs")
            && event.getChannel().getName().contains("private");
        if (!isFowCombatThread) {
            return false;
        }

        String gameName = event.getChannel().getName().substring(0, event.getChannel().getName().indexOf("-"));
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (!managedGame.isFowMode()) {
            return false;
        }

        Game game = managedGame.getGame();
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

        boolean isPlayerInvalid = player3 == null || !player3.isRealPlayer() || !event.getChannel().getName().contains(player3.getColor());
        boolean isBotMessage = event.getAuthor().isBot();
        boolean isTotalHitsMessage = messageText.contains("Total hits ");
        if ((isPlayerInvalid || isBotMessage) && (!isBotMessage || !isTotalHitsMessage)) {
            return false;
        }
        if (StringUtils.countMatches(event.getChannel().getName(), "-") <= 4) {
            return false;
        }

        String systemPos = event.getChannel().getName().split("-")[4];

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
        return true;
    }
}
