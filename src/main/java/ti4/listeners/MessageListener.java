package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.executors.ExecutorManager;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.FOWCombatThreadMirroring;
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

        ExecutorManager.runAsync("MessageListener task", EXECUTION_TIME_WARNING_THRESHOLD_SECONDS, () -> processMessage(event, message));
    }

    private static void processMessage(@Nonnull MessageReceivedEvent event, Message message) {
        try {
            if (!event.getAuthor().isBot()) {
                if (checkForFogOfWarInvitePrompt(message)) return;
                if (copyLFGPingsToLFGPingsChannel(event, message)) return;

                String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
                if (GameManager.isValid(gameName)) {
                    if (handleWhispers(event, message, gameName)) return;
                    if (endOfRoundSummary(event, message, gameName)) return;
                    if (addFactionEmojiReactionsToMessages(event, gameName)) return;
                }
            }
            handleFogOfWarCombatThreadMirroring(event);
        } catch (Exception e) {
            BotLogger.error("`MessageListener.onMessageReceived`   Error trying to handle a received message:\n> " +
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
        if (!message.getContentRaw().toLowerCase().contains("where no stroter has gone before")) {
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

    private static boolean endOfRoundSummary(MessageReceivedEvent event, Message message, String gameName) {
        if (!message.getContentRaw().toLowerCase().startsWith("endofround")) {
            return false;
        }
        String messageText = message.getContentRaw();
        String messageBeginning = StringUtils.substringBefore(messageText, " ");
        String messageContent = StringUtils.substringAfter(messageText, " ");

        Game game = GameManager.getManagedGame(gameName).getGame();
        Player player = getPlayer(event, game);
        RoundSummaryHelper.storeEndOfRoundSummary(game, player, messageBeginning, messageContent, true, event.getChannel());
        GameManager.save(game, "End of round summary.");
        return true;
    }

    private static boolean handleWhispers(MessageReceivedEvent event, Message message, String gameName) {
        if (message.getContentRaw().contains("used /fow whisper")) {
            message.delete().queue();
        }

        String messageText = message.getContentRaw();
        if (!messageText.toLowerCase().startsWith("to") || !messageText.contains(" ")) {
            return false;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        if (game == null) {
            return true;
        }
        
        //Prevent whispers from fow combat threads
        if (game.isFowMode() && event.getChannel() instanceof ThreadChannel
            && event.getChannel().getName().contains("vs")
            && event.getChannel().getName().contains("private")) {
            return false;
        }

        Player sender = getPlayer(event, game);
        if (sender == null || !sender.isRealPlayer()) {
            return true;
        }

        String messageLowerCase = messageText.toLowerCase();
        String receivingColorOrFaction = StringUtils.substringBetween(messageLowerCase, "to", " ").replaceAll("[^a-zA-Z0-9]+$", "");

        if ("futureme".equals(receivingColorOrFaction)) {
            whisperToFutureMe(event, game, sender);
            GameManager.save(game, "Whisper to future by " + sender.getUserName());
            return true;
        }

        boolean future = receivingColorOrFaction.startsWith("future");
        receivingColorOrFaction = receivingColorOrFaction.replaceFirst("future", "");
        if (receivingColorOrFaction.isEmpty()) {
            return true;
        }

        receivingColorOrFaction = AliasHandler.resolveFaction(receivingColorOrFaction);
        if (!Mapper.isValidColor(receivingColorOrFaction) && !Mapper.isValidFaction(receivingColorOrFaction)) {
            return true;
        }

        String messageContent = StringUtils.substringAfter(messageText, " ");
        if (messageContent.isEmpty()) {
            message.reply("No message content?").queue();
            return true;
        }

        Player receiver = null;
        for (Player player : game.getRealPlayers()) {
            if (Objects.equals(receivingColorOrFaction, player.getFaction()) || Objects.equals(receivingColorOrFaction, player.getColor())) {
                receiver = player;
                break;
            }
        }

        if (receiver == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found: " + receivingColorOrFaction);
            return true;
        }

        if (future) {
            whisperToFutureColorOrFaction(event, game, messageContent, sender, receiver);
        } else {
            WhisperService.sendWhisper(game, sender, receiver, messageContent, "n", event.getChannel(), event.getGuild());
            message.delete().queue();
        }
        GameManager.save(game, "Whisper");
        return true;
    }

    private static void whisperToFutureColorOrFaction(MessageReceivedEvent event, Game game, String messageContent, Player sender, Player receiver) {
        String futureMsgKey = "futureMessageFor_" + receiver.getFaction() + "_" + sender.getFaction();
        game.setStoredValue(futureMsgKey, game.getStoredValue(futureMsgKey) + "\n\n" + messageContent);
        MessageHelper.sendMessageToChannel(event.getChannel(), sender.getFactionEmoji() + " sent someone else a future message");
        MessageHelper.sendMessageToPlayerCardsInfoThread(sender, "You sent a future message to " + receiver.getRepresentationNoPing() + ":\n>>> " + messageContent);
        event.getMessage().delete().queue();
    }

    private static void whisperToFutureMe(MessageReceivedEvent event, Game game, Player player) {
        String messageContent = StringUtils.substringAfter(event.getMessage().getContentRaw(), " ");

        String previousThoughts = "";
        if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            previousThoughts = game.getStoredValue("futureMessageFor" + player.getFaction()) + "\n\n";
        }
        game.setStoredValue("futureMessageFor" + player.getFaction(), previousThoughts + messageContent);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " sent themselves a future message");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, "You sent yourself a future message:\n>>> " + messageContent);
        event.getMessage().delete().queue();
    }

    private static boolean addFactionEmojiReactionsToMessages(MessageReceivedEvent event, String gameName) {
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
            BotLogger.error("Reading previous message", e);
        }
        return true;
    }

    /**
     * replicate messages in combat threads so that observers can see
     */
    private static void handleFogOfWarCombatThreadMirroring(MessageReceivedEvent event) {
        if (!AsyncTI4DiscordBot.fowServers.isEmpty() && // fog servers exists
            !AsyncTI4DiscordBot.fowServers.contains(event.getGuild()) && // event server IS NOT the fog server
            !AsyncTI4DiscordBot.guildCommunityPlays.getId().equals(event.getGuild().getId()) && // NOR the community server
            AsyncTI4DiscordBot.guildPrimaryID.equals(Constants.ASYNCTI4_HUB_SERVER_ID)) {// bot is running in production
            return;
        } // else it's probably a dev/test server, so execute

        FOWCombatThreadMirroring.mirrorEvent(event);
    }
}
