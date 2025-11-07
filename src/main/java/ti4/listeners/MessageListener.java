package ti4.listeners;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.fow.WhisperService;
import ti4.service.game.CreateGameService;
import ti4.service.game.GameNameService;
import ti4.spring.jda.JdaService;

public class MessageListener extends ListenerAdapter {

    private static final int EXECUTION_TIME_WARNING_THRESHOLD_SECONDS = 1;
    private static final Pattern FUTURE = Pattern.compile("future");
    private static final Pattern PATTERN = Pattern.compile("[^a-zA-Z0-9]+$");
    // The mention itself is 23 characters long
    private static final int BOTHELPER_MENTION_REMINDER_MESSAGE_LENGTH_THRESHOLD = 53;
    private static final String BOTHELPER_MENTION_REMINDER_TEXT =
            """
        Friendly reminder in case you forgot, please include the specific reason for the ping (e.g. something is not working, there is a bug, or you're not sure how to do something) and any other relevant information. This will speed up the process by allowing the staff to know how they can help. Thanks!
            """;

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!JdaService.isReadyToReceiveCommands()
                || !JdaService.isValidGuild(event.getGuild().getId())) {
            return;
        }

        Message message = event.getMessage();
        if (message.getContentRaw().startsWith("[DELETE]")) {
            message.delete().queue();
            return;
        }

        ExecutorServiceManager.runAsync(
                "MessageListener task", EXECUTION_TIME_WARNING_THRESHOLD_SECONDS, () -> processMessage(event, message));
    }

    private static void sendMessageToModLog(String msg) {
        TextChannel moderationLogChannel =
                JdaService.guildPrimary.getTextChannelsByName("interesting-messages-log", true).stream()
                        .findFirst()
                        .orElse(null);
        if (moderationLogChannel != null) {
            MessageHelper.sendMessageToChannel(moderationLogChannel, msg);
        }
    }

    private static void processMessage(@Nonnull MessageReceivedEvent event, Message message) {
        try {
            if (!event.getAuthor().isBot()) {
                if (respondToBotHelperPing(message)) return;
                if (checkForFogOfWarInvitePrompt(message)) return;
                if (copyLFGPingsToLFGPingsChannel(event, message)) return;
                if (message.getContentRaw().toLowerCase().contains("gaslight")) {
                    String msg = "Someone used gaslight here: " + message.getJumpUrl() + "\nFull msg: "
                            + message.getContentRaw();
                    sendMessageToModLog(msg);
                }
                if (message.getContentRaw().toLowerCase().contains("please stop")) {
                    String msg = "Someone used please stop here: " + message.getJumpUrl() + "\nFull msg: "
                            + message.getContentRaw();
                    sendMessageToModLog(msg);
                }

                String gameName = GameNameService.getGameNameFromChannel(event.getChannel());
                if (GameManager.isValid(gameName)) {
                    if (handleWhispers(event, message, gameName)) return;
                    if (endOfRoundSummary(event, message, gameName)) return;
                    if (addFactionEmojiReactionsToMessages(event, gameName)) return;
                }
            }
            handleFogOfWarCombatThreadMirroring(event);
        } catch (Exception e) {
            BotLogger.error(
                    "`MessageListener.onMessageReceived`   Error trying to handle a received message:\n> "
                            + event.getMessage().getJumpUrl(),
                    e);
        }
    }

    private static boolean respondToBotHelperPing(Message message) {
        boolean messageLikelyMissingExplanation =
                message.getContentRaw().length() < BOTHELPER_MENTION_REMINDER_MESSAGE_LENGTH_THRESHOLD;
        boolean messageMentionsBotHelper = message.getMentions().getRoles().stream()
                .anyMatch(mentionedRole -> JdaService.bothelperRoles.stream()
                        .anyMatch(bothelperRole -> bothelperRole.getIdLong() == mentionedRole.getIdLong()));
        boolean shouldRespondToBotHelperPing = messageLikelyMissingExplanation && messageMentionsBotHelper;
        if (shouldRespondToBotHelperPing) {
            message.reply(BOTHELPER_MENTION_REMINDER_TEXT).queue();
        }
        return shouldRespondToBotHelperPing;
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
        message.reply(
                        "to explore strange new maps; to seek out new tiles and new factions\nhttps://discord.gg/RZ7qg9kbVZ")
                .queue();
        return true;
    }

    private static boolean copyLFGPingsToLFGPingsChannel(MessageReceivedEvent event, Message message) {
        if (!(event.getChannel() instanceof ThreadChannel)) {
            return false;
        }
        Role lfgRole = CreateGameService.getRole("LFG", event.getGuild()); // 947310962485108816
        if (lfgRole == null || !message.getContentRaw().contains(lfgRole.getAsMention())) {
            return false;
        }
        String msg2 = lfgRole.getAsMention()
                + " this game is looking for more members (it's old if it has -launched [FULL] in its title) "
                + message.getJumpUrl();
        TextChannel lfgPings = JdaService.guildPrimary.getTextChannelsByName("lfg-pings", true).stream()
                .findFirst()
                .orElse(null);
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
        RoundSummaryHelper.storeEndOfRoundSummary(
                game, player, messageBeginning, messageContent, true, event.getChannel());
        GameManager.save(
                game,
                "End of round summary."); // TODO: We should be locking since we're saving. Convert to ListenerContext?
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

        // Prevent whispers from fow combat threads
        if (game.isFowMode()
                && event.getChannel() instanceof ThreadChannel
                && event.getChannel().getName().contains("vs")
                && event.getChannel().getName().contains("private")) {
            return false;
        }

        Player sender = getPlayer(event, game);
        if (sender == null || !sender.isRealPlayer()) {
            return true;
        }

        String messageLowerCase = messageText.toLowerCase();
        String receivingColorOrFaction = PATTERN.matcher(StringUtils.substringBetween(messageLowerCase, "to", " "))
                .replaceAll("");

        if ("futureme".equals(receivingColorOrFaction)) {
            whisperToFutureMe(event, game, sender);
            GameManager.save(
                    game,
                    "Whisper to future by " + sender.getUserName()); // TODO: We should be locking since we're saving
            return true;
        }

        boolean future = receivingColorOrFaction.startsWith("future");
        receivingColorOrFaction = FUTURE.matcher(receivingColorOrFaction).replaceFirst("");
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
            if (Objects.equals(receivingColorOrFaction, player.getFaction())
                    || Objects.equals(receivingColorOrFaction, player.getColor())) {
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
            WhisperService.sendWhisper(
                    game, sender, receiver, messageContent, "n", event.getChannel(), event.getGuild());
            message.delete().queue();
        }
        GameManager.save(game, "Whisper"); // TODO: We should be locking since we're saving
        return true;
    }

    private static void whisperToFutureColorOrFaction(
            MessageReceivedEvent event, Game game, String messageContent, Player sender, Player receiver) {
        String futureMsgKey = "futureMessageFor_" + receiver.getFaction() + "_" + sender.getFaction();
        game.setStoredValue(futureMsgKey, game.getStoredValue(futureMsgKey) + "\n\n" + messageContent);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), sender.getFactionEmoji() + " sent someone else a future message");
        MessageHelper.sendMessageToPlayerCardsInfoThread(
                sender,
                "You sent a future message to " + receiver.getRepresentationNoPing() + ":\n>>> " + messageContent);
        event.getMessage().delete().queue();
    }

    private static void whisperToFutureMe(MessageReceivedEvent event, Game game, Player player) {
        String messageContent = StringUtils.substringAfter(event.getMessage().getContentRaw(), " ");

        String previousThoughts = "";
        if (!game.getStoredValue("futureMessageFor" + player.getFaction()).isEmpty()) {
            previousThoughts = game.getStoredValue("futureMessageFor" + player.getFaction()) + "\n\n";
        }
        game.setStoredValue("futureMessageFor" + player.getFaction(), previousThoughts + messageContent);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getFactionEmoji() + " sent themselves a future message");
        MessageHelper.sendMessageToPlayerCardsInfoThread(
                player, "You sent yourself a future message:\n>>> " + messageContent);
        event.getMessage().delete().queue();
    }

    private static boolean addFactionEmojiReactionsToMessages(MessageReceivedEvent event, String gameName) {
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame.getGame().isHiddenAgendaMode()
                && managedGame.getGame().getPhaseOfGame().toLowerCase().contains("agenda")) {
            Player player = getPlayer(event, managedGame.getGame());
            if (player == null
                    || !player.isRealPlayer()
                    || event.getChannel().getId().equals(player.getCardsInfoThreadID())
                    || managedGame.isFowMode() && event.getChannel().equals(player.getPrivateChannel())) {
                return false;
            }
            if (!player.isSpeaker()) {
                event.getChannel().getHistory().retrievePast(1).queue(messages -> {
                    var emoji = Emoji.fromFormatted("🤫");
                    messages.getFirst().addReaction(emoji).queue();
                });
            }
        }
        if (!managedGame.isFactionReactMode() && !managedGame.isColorReactMode() && !managedGame.isStratReactMode()
                || managedGame.isFowMode()) {
            return false;
        }
        Game game = managedGame.getGame();
        Player player = getPlayer(event, game);
        if (player == null || !player.isRealPlayer()) {
            return false;
        }
        try {
            event.getChannel().getHistory().retrievePast(2).queue(messages -> {
                if (messages.size() == 2
                        && !event.getMessage()
                                .getAuthor()
                                .getId()
                                .equalsIgnoreCase(messages.get(1).getAuthor().getId())) {
                    if (managedGame.isFactionReactMode()) {
                        var emoji = Emoji.fromFormatted(player.getFactionEmoji());
                        messages.getFirst().addReaction(emoji).queue();
                    }
                    if (managedGame.isColorReactMode()) {
                        var emoji = ColorEmojis.getColorEmoji(player.getColor()).asEmoji();
                        messages.getFirst().addReaction(emoji).queue();
                    }
                    if (managedGame.isStratReactMode()) {
                        if (game.getPhaseOfGame().contains("action")
                                && !game.isHomebrewSCMode()
                                && player.getLowestSC() != 100) {

                            for (Integer sc : player.getSCs()) {
                                var emoji2 = CardEmojis.getSCFrontFromInteger(sc);
                                if (game.getPlayedSCs().contains(sc)) {
                                    emoji2 = CardEmojis.getSCBackFromInteger(sc);
                                }
                                if (emoji2 != null && emoji2.asEmoji() != null) {
                                    var demoji2 = emoji2.asEmoji();
                                    messages.getFirst().addReaction(demoji2).queue();
                                }
                            }
                        }
                    }
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
        if (!JdaService.fowServers.isEmpty()
                && // fog servers exists
                !JdaService.fowServers.contains(event.getGuild())
                && // event server IS NOT the fog server
                !JdaService.guildCommunityPlays.getId().equals(event.getGuild().getId())
                && // NOR the community server
                JdaService.guildPrimaryID.equals(Constants.ASYNCTI4_HUB_SERVER_ID)) { // bot is running in production
            return;
        } // else it's probably a dev/test server, so execute

        FOWCombatThreadMirroring.mirrorEvent(event);
    }
}
