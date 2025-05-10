package ti4.message;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.dv8tion.jda.api.entities.Guild;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiscordWebhook;
import ti4.helpers.Helper;
import ti4.helpers.ThreadArchiveHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.service.actioncard.SabotageService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.game.GameNameService;
import ti4.service.game.GameUndoNameService;

public class MessageHelper {

    public interface MessageFunction {
        void run(Message msg);
    }

    public static void sendMessageToChannel(MessageChannel channel, String messageText) {
        splitAndSent(messageText, channel);
    }

    public static void sendMessageToEventChannel(GenericInteractionCreateEvent event, String messageText) {
        sendMessageToChannel(event.getMessageChannel(), messageText);
    }

    public static void sendMessageToEventServerBotLogChannel(GenericInteractionCreateEvent event, String messageText) {
        splitAndSent(messageText, BotLogger.getBotLogChannel(event));
    }

    public static void sendMessageToChannelWithEmbed(MessageChannel channel, String messageText, MessageEmbed embed) {
        splitAndSent(messageText, channel, Collections.singletonList(embed), null);
    }

    public static void sendMessageToChannelWithEmbeds(MessageChannel channel, String messageText, List<MessageEmbed> embeds) {
        splitAndSent(messageText, channel, embeds, null);
    }

    public static void sendMessageToChannelWithButton(MessageChannel channel, String messageText, Button button) {
        splitAndSent(messageText, channel, null, Collections.singletonList(button));
    }

    public static void sendMessageToEventChannelWithEphemeralButtons(ButtonInteractionEvent event, String message, List<Button> buttons) {
        List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(message, buttons);
        for (MessageCreateData messageD : messageList) {
            event.getHook().setEphemeral(true).sendMessage(messageD).queue();
        }
    }

    public static void sendEphemeralMessageToEventChannel(ButtonInteractionEvent event, String message) {
        event.getHook().setEphemeral(true).sendMessage(message).queue();
    }

    public static void sendMessageToChannelWithButtons(MessageChannel channel, String messageText, List<Button> buttons) {
        String gameName = GameNameService.getGameNameFromChannel(channel);
        if (GameManager.isValid(gameName) && buttons instanceof ArrayList && !(channel instanceof ThreadChannel) && channel.getName().contains("actions")) {
            buttons = addUndoButtonToList(buttons, gameName);
        }
        sendMessageToChannelWithEmbedsAndButtons(channel, messageText, null, buttons);
    }

    public static void sendMessageToChannelWithButtonsAndNoUndo(MessageChannel channel, String messageText, List<Button> buttons) {
        sendMessageToChannelWithEmbedsAndButtons(channel, messageText, null, buttons);
    }

    public static void sendMessageToChannelWithEmbedsAndButtons(@Nonnull MessageChannel channel, @Nullable String messageText, @Nullable List<MessageEmbed> embeds, @Nullable List<Button> buttons) {
        splitAndSent(messageText, channel, embeds, buttons);
    }

    public static List<Button> addUndoButtonToList(List<Button> buttons, String gameName) {
        for (Button button : buttons) {
            if (button.getId().contains("ultimateUndo")) {
                return buttons;
            }
        }

        try {
            int maxNumber = GameUndoNameService.getLatestUndoNumber(gameName);
            if (maxNumber == -1) {
                return buttons;
            }
            List<Button> newButtons = new ArrayList<>(buttons);
            newButtons.add(Buttons.gray("ultimateUndo_" + maxNumber, "UNDO"));
            return newButtons;
        } catch (Exception e) {
            BotLogger.error("Error trying to make undo copy for map: " + gameName, e);
            return buttons;
        }
    }

    private static void addFactionReactToMessage(Game game, Player player, Message message) {
        Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, message);
        if (reactionEmoji != null) {
            message.addReaction(reactionEmoji).queue(null,
                error -> BotLogger.error(getRestActionFailureMessage(message.getChannel(), "Failed to add reaction to message", null, error), error));
        }
        String messageId = message.getId();
        GameMessageManager.addReaction(game.getName(), player.getFaction(), messageId);
    }

    public static void sendMessageToChannelWithFactionReact(MessageChannel channel, String messageText, Game game, Player player, List<Button> buttons) {
        sendMessageToChannelWithFactionReact(channel, messageText, game, player, buttons, false);
    }

    public static void sendMessageToChannelWithFactionReact(MessageChannel channel, String messageText, Game game, Player player, List<Button> buttons, boolean saboable) {
        sendMessageToChannelWithEmbedsAndFactionReact(channel, messageText, game, player, null, buttons, saboable);
    }

    public static void sendMessageToChannelWithEmbedsAndFactionReact(
        MessageChannel channel, String messageText, Game game, Player player,
        List<MessageEmbed> embeds, List<Button> buttons, boolean saboable
    ) {
        MessageFunction addFactionReact = (message) -> {
            if (saboable) {
                GameMessageManager.add(game.getName(), message.getId(), GameMessageType.ACTION_CARD, game.getLastModifiedDate());
            }
            addFactionReactToMessage(game, player, message);
            if (!saboable) {
                return;
            }
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player || SabotageService.couldFeasiblySabotage(p2, game)) {
                    continue;
                }
                addFactionReactToMessage(game, p2, message);
            }
            ReactionService.progressGameIfAllPlayersHaveReacted(message.getId(), game);
        };
        splitAndSentWithAction(messageText, channel, addFactionReact, embeds, buttons);
    }

    public static void sendMessageToChannelWithPersistentReacts(MessageChannel channel, String messageText, Game game, List<Button> buttons, GameMessageType messageType) {
        MessageFunction addFactionReact = (message) -> {
            StringTokenizer players = switch (messageType) {
                case AGENDA_WHEN -> {
                    String oldMessageId = GameMessageManager.replace(game.getName(), message.getId(), GameMessageType.AGENDA_WHEN, game.getLastModifiedDate());
                    if (oldMessageId != null) {
                        game.getMainGameChannel().deleteMessageById(oldMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                    yield new StringTokenizer(game.getPlayersWhoHitPersistentNoWhen(), "_");
                }
                case AGENDA_AFTER -> {
                    String oldMessageId = GameMessageManager.replace(game.getName(), message.getId(), GameMessageType.AGENDA_AFTER, game.getLastModifiedDate());
                    if (oldMessageId != null) {
                        game.getMainGameChannel().deleteMessageById(oldMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                    yield new StringTokenizer(game.getPlayersWhoHitPersistentNoAfter(), "_");
                }
                case AGENDA_CONFOUNDING_CONFUSING_LEGAL_TEXT -> {
                    String oldMessageId = GameMessageManager.replace(game.getName(), message.getId(), GameMessageType.AGENDA_CONFOUNDING_CONFUSING_LEGAL_TEXT, game.getLastModifiedDate());
                    if (oldMessageId != null) {
                        game.getMainGameChannel().deleteMessageById(oldMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                    yield new StringTokenizer(game.getStoredValue("Pass On Shenanigans"), "_");
                }
                case AGENDA_DEADLY_PLOT -> {
                    String oldMessageId = GameMessageManager.replace(game.getName(), message.getId(), GameMessageType.AGENDA_DEADLY_PLOT, game.getLastModifiedDate());
                    if (oldMessageId != null) {
                        game.getMainGameChannel().deleteMessageById(oldMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                    yield new StringTokenizer(game.getStoredValue("Pass On Shenanigans"), "_");
                }
                default -> {
                    BotLogger.warning(new BotLogger.LogMessageOrigin(game), "Unable to handle message type: " + messageType);
                    yield null;
                }
            };

            while (players != null && players.hasMoreTokens()) {
                String playerString = players.nextToken();
                Player player = game.getPlayerFromColorOrFaction(playerString);
                addFactionReactToMessage(game, player, message);
            }
        };
        splitAndSentWithAction(messageText, channel, addFactionReact, null, buttons);
    }

    public static void sendMessageToChannelAndPin(MessageChannel channel, String messageText) {
        MessageFunction pin = (msg) -> msg.pin().queue(null,
            error -> BotLogger.error(getRestActionFailureMessage(channel, "Failed to pin message", null, error), error));
        splitAndSentWithAction(messageText, channel, pin);
    }

    public static void sendFileToChannel(MessageChannel channel, File file) {
        FileUpload fileUpload = FileUpload.fromData(file);
        sendFileUploadToChannel(channel, fileUpload);
    }

    public static void sendFileUploadToChannel(MessageChannel channel, FileUpload fileUpload) {
        if (fileUpload == null) {
            BotLogger.error("FileUpload null");
            return;
        }
        channel.sendFiles(fileUpload).queue(null,
            error -> BotLogger.error(getRestActionFailureMessage(channel, "Failed to send File to Channel", null, error), error));
    }

    public static void sendEphemeralFileInResponseToButtonPress(FileUpload fileUpload, GenericInteractionCreateEvent event) {
        if (fileUpload == null) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "FileUpload null");
            return;
        }
        if (event instanceof ButtonInteractionEvent button)
            button.getHook().sendMessage("Here is your requested image").addFiles(fileUpload).setEphemeral(true).queue();
        else if (event instanceof SlashCommandInteractionEvent slash)
            slash.getHook().sendMessage("Here is your requested image").addFiles(fileUpload).setEphemeral(true).queue();
    }

    public static void sendFileToChannelAndAddLinkToButtons(MessageChannel channel, FileUpload fileUpload, String message, List<Button> buttons) {
        if (fileUpload == null) {
            BotLogger.error("FileUpload null");
            return;
        }
        final List<Button> realButtons = new ArrayList<>();
        channel.sendFiles(fileUpload).queue(msg -> {
            String link = msg.getAttachments().getFirst().getUrl();
            realButtons.add(Button.link(link, "Open in browser"));
            realButtons.addAll(buttons);
            splitAndSent(message, channel, null, realButtons);
        }, BotLogger::catchRestError);
    }

    public static void sendFileToChannelWithButtonsAfter(MessageChannel channel, FileUpload fileUpload, String message, List<Button> buttons) {
        sendFileUploadToChannel(channel, fileUpload);
        splitAndSent(message, channel, null, buttons);
    }

    public static void replyToMessage(GenericInteractionCreateEvent event, String messageText) {
        splitAndSent(messageText, event.getMessageChannel());
    }

    public static void replyToMessage(GenericInteractionCreateEvent event, FileUpload fileUpload) {
        replyToMessage(event, fileUpload, false, null, false);
    }

    public static void replyToMessage(GenericInteractionCreateEvent event, FileUpload fileUpload, boolean forceShowMap) {
        replyToMessage(event, fileUpload, forceShowMap, null, false);
    }

    public static void editMessageButtons(ButtonInteractionEvent event, List<Button> buttons) {
        editMessageWithButtons(event, event.getMessage().getContentRaw(), buttons);
    }

    public static void editMessageWithButtons(ButtonInteractionEvent event, String message, List<Button> buttons) {
        editMessageWithButtonsAndFiles(event, message, buttons, Collections.emptyList());
    }

    public static void editMessageWithButtonsAndFiles(ButtonInteractionEvent event, String message, List<Button> buttons, List<FileUpload> files) {
        editMessageWithActionRowsAndFiles(event, message, ActionRow.partitionOf(buttons), files);
    }

    public static void editMessageWithActionRowsAndFiles(ButtonInteractionEvent event, String message, List<ActionRow> rows, List<FileUpload> files) {
        event.getHook().editOriginal(message).setComponents(rows).setFiles(files).queue();
    }

    public static void replyToMessage(GenericInteractionCreateEvent event, FileUpload fileUpload, boolean forceShowMap, String messageText, boolean pinMessage) {
        try {
            if (forceShowMap && event.getChannel() instanceof MessageChannel) {
                sendMessageWithFile((MessageChannel) event.getChannel(), fileUpload, messageText, pinMessage);
                return;
            }
            if (event.getChannel() instanceof MessageChannel) {
                sendMessageWithFile((MessageChannel) event.getChannel(), fileUpload, messageText, pinMessage);
            }
        } catch (Exception e) {
            replyToMessage(event, "Could not send response, use `/show_game` or contact Admins or Bothelper.");
        }
    }

    public static void sendMessageWithFile(MessageChannel channel, FileUpload fileUpload, String messageText, boolean pinMessage) {
        if (channel.getName().contains("-actions")) {
            String threadName = channel.getName().replace("-actions", "") + "-bot-map-updates";
            List<ThreadChannel> threadChannels = ((IThreadContainer) channel).getThreadChannels();
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                    channel = threadChannel_;
                }
            }
        }

        MessageCreateBuilder message = new MessageCreateBuilder();
        if (messageText != null) {
            message.addContent(messageText);
        }
        MessageCreateData messageObject = message.addFiles(fileUpload).build();
        channel.sendMessage(messageObject).queue(msg -> {
            if (pinMessage)
                msg.pin().queue();
        });
    }

    private static void splitAndSent(String messageText, MessageChannel channel) {
        splitAndSent(messageText, channel, null, null);
    }

    private static void splitAndSent(String messageText, MessageChannel channel, List<MessageEmbed> embeds, List<Button> buttons) {
        splitAndSentWithAction(messageText, channel, null, embeds, buttons);
    }

    public static void splitAndSentWithAction(String messageText, MessageChannel channel, MessageFunction restAction) {
        splitAndSentWithAction(messageText, channel, restAction, null, null);
    }

    public static void splitAndSentWithAction(String messageText, MessageChannel channel, List<Button> buttons, MessageFunction restAction) {
        splitAndSentWithAction(messageText, channel, restAction, null, buttons);
    }

    private static void splitAndSentWithAction(String messageText, MessageChannel channel, MessageFunction restAction, List<MessageEmbed> embeds, List<Button> buttons) {
        if (channel == null) {
            return;
        }

        List<MessageEmbed> sanitizedEmbeds;
        if (embeds == null) {
            sanitizedEmbeds = Collections.emptyList();
        } else {
            sanitizedEmbeds = embeds.stream().filter(Objects::nonNull).collect(Collectors.toList());
        }

        if (channel instanceof ThreadChannel thread) {
            if (thread.isArchived() && !thread.isLocked()) {
                String txt = messageText;
                List<Button> butts = buttons;
                thread.getManager().setArchived(false).queue((v) -> splitAndSentWithAction(txt, channel, restAction, sanitizedEmbeds, butts), BotLogger::catchRestError);
                return;
            } else if (thread.isLocked()) {
                BotLogger.warning("WARNING: Attempting to send a message to locked thread: " + thread.getJumpUrl());
            }
        }

        buttons = sanitizeButtons(buttons, channel);

        String gameName = GameNameService.getGameNameFromChannel(channel);
        if (GameManager.isValid(gameName)) {
            ManagedGame managedGame = GameManager.getManagedGame(gameName);
            if (!managedGame.isFowMode()) {
                Game game = managedGame.getGame();
                if (game.isInjectRulesLinks()) {
                    messageText = injectRules(messageText);
                }
            }
        }

        final String finalMessageText = messageText;
        List<MessageCreateData> objects = getMessageCreateDataObjects(finalMessageText, sanitizedEmbeds, buttons);
        Iterator<MessageCreateData> iterator = objects.iterator();
        while (iterator.hasNext()) {
            MessageCreateData messageCreateData = iterator.next();
            if (iterator.hasNext()) { // not last message
                channel.sendMessage(messageCreateData).queue(null,
                    error -> BotLogger.error(getRestActionFailureMessage(channel, "Failed to send intermediate message", messageCreateData, error), error));
            } else { // last message, do action
                channel.sendMessage(messageCreateData).queue(message -> {
                    ManagedGame managedGame = GameManager.getManagedGame(gameName);
                    if (finalMessageText != null && managedGame != null && !managedGame.isFowMode()) {
                        if (finalMessageText.contains("Use buttons to do your turn") || finalMessageText.contains("Use buttons to end turn")) {
                            GameMessageManager.replace(gameName, message.getId(), GameMessageType.TURN, managedGame.getLastModifiedDate());
                        }
                    }

                    if (restAction != null) {
                        restAction.run(message);
                    }
                }, error -> BotLogger.error(getRestActionFailureMessage(channel, finalMessageText, messageCreateData, error), error));
            }
        }
    }

    public static String getRestActionFailureMessage(MessageChannel channel, String errorHeader, MessageCreateData messageCreateData, Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append(channel.getAsMention()).append("\nRestAction Failure within MessageHelper.splitAndSentWithAction: ");
        sb.append(errorHeader);
        sb.append("\n```").append(error.getMessage()).append("```");
        if (messageCreateData != null) {
            String messageJSON = messageCreateData.toData().toPrettyString();
            sb.append("\nMessageContent: ").append(messageCreateData.getContent());
            int maxJSONLength = 1500;
            if (messageJSON.length() < maxJSONLength) {
                sb.append("\nJSON:\n```json").append(messageJSON).append("```");
            } else {
                sb.append("\nJSON:\n```json").append(StringUtils.left(messageJSON, maxJSONLength)).append("```");
                sb.append("\nMessageData JSON was too long and was truncated");
            }
        }
        return sb.toString();
    }

    /**
     * Send a private message to the player.
     *
     * @param player Player to send a message to
     * @param game Active map
     * @param event Event that caused the message
     * @param messageText Message to send
     * @param failText Feedback if the message failed to send
     * @param successText Feedback if the message successfully sent
     * @return True if the message was send successfully, false otherwise
     */
    public static boolean sendPrivateMessageToPlayer(Player player, Game game, GenericInteractionCreateEvent event, String messageText, String failText, String successText) {
        return sendPrivateMessageToPlayer(player, game, event.getMessageChannel(), messageText, failText, successText);
    }

    /**
     * Send a private message to the player.
     * <p>
     * This implementation does not provide feedback
     *
     * @param player Player to send a message to
     * @param game Active map
     * @param messageText Message to send
     * @return True if the message was sent successfully, false otherwise
     */
    public static boolean sendPrivateMessageToPlayer(Player player, Game game, String messageText) {
        if (player.getUser().isBot() && !game.isCommunityMode()) {
            return true;
        }
        return sendPrivateMessageToPlayer(player, game, (MessageChannel) null, messageText, null, null);
    }

    /**
     * Send a private message to the player.
     *
     * @param player Player to send a message to
     * @param game Active map
     * @param feedbackChannel Channel to send feedback to
     * @param messageText Message to send
     * @param failText Feedback if the message failed to send
     * @param successText Feedback if the message successfully sent
     * @return True if the message was send successfully, false otherwise
     */
    public static boolean sendPrivateMessageToPlayer(Player player, Game game, MessageChannel feedbackChannel, String messageText, String failText, String successText) {
        if (messageText == null || messageText.isEmpty())
            return true; // blank message counts as a success
        User user = AsyncTI4DiscordBot.jda.getUserById(player.getUserID());
        if (user == null) {
            sendMessageToChannel(feedbackChannel, failText);
            return false;
        } else {
            MessageChannel privateChannel = player.getPrivateChannel();
            if (!game.isFowMode()) {
                privateChannel = player.getCardsInfoThread();
            }
            if (privateChannel == null) {
                sendMessageToUser(game.getName() + " " + messageText, user);
            } else {
                sendMessageToChannel(privateChannel, messageText);
            }
            sendMessageToChannel(feedbackChannel, successText);
            return true;
        }
    }

    public static boolean privatelyPingPlayerList(List<Player> players, Game game, String message) {
        return privatelyPingPlayerList(players, game, null, message, null, null);
    }

    public static boolean privatelyPingPlayerList(
        List<Player> players, Game game, MessageChannel feedbackChannel,
        String message, String failText, String successText
    ) {
        int count = 0;
        for (Player player : players) {
            String playerRepresentation = player.getRepresentationUnfogged();
            boolean success = sendPrivateMessageToPlayer(player, game, feedbackChannel,
                playerRepresentation + message, failText, successText);
            if (success)
                count++;
        }
        return count == players.size();
    }

    public static void sendMessageToUser(String messageText, GenericInteractionCreateEvent event) {
        sendMessageToUser(messageText, event.getUser());
    }

    public static void sendMessageToUser(String messageText, User user) {
        user.openPrivateChannel().queue(channel -> splitAndSent(messageText, channel));
    }

    /**
     * @param player Player to send the messageText
     * @param messageText messageText - handles large text ()>1500 chars)
     */
    public static void sendMessageToPlayerCardsInfoThread(@NotNull Player player, String messageText) {
        if (messageText == null || messageText.isEmpty()) {
            return;
        }

        // GET CARDS INFO THREAD
        ThreadChannel threadChannel = player.getCardsInfoThread();

        sendMessageToChannel(threadChannel, messageText);
    }

    /**
     * Given a text string and a maximum length, will return a List<String> split by
     * either the max length or the last newline "\n"
     *
     * @param messageText any non-null, non-empty string
     * @param maxLength maximum length, any positive integer
     */
    private static List<String> splitLargeText(String messageText, int maxLength) {
        List<String> texts = new ArrayList<>();
        if (messageText == null || messageText.isEmpty())
            return Collections.emptyList();
        int messageLength = messageText.length();
        if (messageLength <= maxLength)
            return Collections.singletonList(messageText);
        int index = 0;
        while (index < messageLength) {
            String nextChars = messageText.substring(index, Math.min(index + maxLength, messageLength));
            int lastNewLineIndex = nextChars.lastIndexOf("\n") + 1; // number of chars until right after the last \n
            String textToAdd;
            if (lastNewLineIndex > 0) {
                textToAdd = nextChars.substring(0, lastNewLineIndex);
                index += lastNewLineIndex;
            } else {
                textToAdd = nextChars;
                index += nextChars.length();
            }
            texts.add(textToAdd);
            if (index + maxLength > messageLength) {
                texts.add(messageText.substring(index));
                break;
            }
        }
        return texts;
    }

    /**
     * @message Message to send - can be large or null/empty
     * @embeds List of MessageEmbed - will truncate after the first 5
     * @buttons List of Button - can be large or null/empty
     *          <p>
     *          </p>
     *          Example of use:
     * 
     *          <pre>
    * {@code
        for (MessageCreateData messageData : getMessageObject(message, embeds, buttons)) {
            channel.sendMessage(messageData).queue();
     * }
     * }
    * </pre>
     */
    public static List<MessageCreateData> getMessageCreateDataObjects(String message, List<MessageEmbed> embeds, List<Button> buttons) {
        List<MessageCreateData> messageCreateDataList = new ArrayList<>();

        List<List<ActionRow>> partitionedButtons = getPartitionedButtonLists(buttons);
        Iterator<List<ActionRow>> buttonIterator = partitionedButtons.iterator();

        List<List<MessageEmbed>> partitionedEmbeds = getPartitionedEmbedLists(embeds);
        Iterator<List<MessageEmbed>> embedsIterator = partitionedEmbeds.iterator();

        List<String> messageList = splitLargeText(message, 2000);
        Iterator<String> messageIterator = messageList.iterator();

        while (messageIterator.hasNext()) {
            String smallMessage = messageIterator.next();

            // More messages exists, so just frontload the plain messages
            if (messageIterator.hasNext() && smallMessage != null && !smallMessage.trim().isEmpty()) {
                messageCreateDataList.add(new MessageCreateBuilder().addContent(smallMessage).build());

                // We are at the last message, so try and add the first row of buttons
            } else if (!messageIterator.hasNext() && smallMessage != null && !smallMessage.trim().isEmpty()) {
                MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
                messageCreateBuilder.addContent(smallMessage);

                // add first set of embeds if it exists
                if (embedsIterator.hasNext()) {
                    List<MessageEmbed> messageEmbeds = embedsIterator.next();
                    if (messageEmbeds != null && !messageEmbeds.isEmpty()) {
                        messageCreateBuilder.addEmbeds(messageEmbeds);
                    }
                }

                // add first row of buttons if it exists AND there are no more embeds
                if (buttonIterator.hasNext() && !embedsIterator.hasNext()) {
                    List<ActionRow> actionRows = buttonIterator.next();
                    if (actionRows != null && !actionRows.isEmpty()) {
                        messageCreateBuilder.addComponents(actionRows);
                    }
                }
                messageCreateDataList.add(messageCreateBuilder.build());
            }
        }

        // ADD REMAINING EMBEDS IF THEY EXIST
        while (embedsIterator.hasNext()) {
            List<MessageEmbed> messageEmbeds = embedsIterator.next();
            if (messageEmbeds != null && !messageEmbeds.isEmpty()) {
                messageCreateDataList.add(new MessageCreateBuilder().addEmbeds(messageEmbeds).build());
            }
        }

        // ADD REMAINING BUTTONS IF THEY EXIST
        while (buttonIterator.hasNext()) {
            List<ActionRow> actionRows = buttonIterator.next();
            if (actionRows != null && !actionRows.isEmpty()) {
                messageCreateDataList.add(new MessageCreateBuilder().addComponents(actionRows).build());
            }
        }

        for (MessageCreateData mcd : messageCreateDataList) {
            if (mcd != null) {
                mcd.getContent();
                continue;
            }
            StringBuilder error = new StringBuilder("MessageCreateData is invalid for arguments: \n");
            int cutoff = message.indexOf("\n");
            error.append("> Message: ").append(cutoff == -1 ? message : message.substring(0, cutoff)).append("...\n");
            error.append("> Buttons:\n");
            for (Button b : buttons) {
                error.append("> - id:`").append(b.getId()).append("`");
            }
            BotLogger.error(error.toString(), null);
            break;
        }
        return messageCreateDataList;
    }

    public static List<MessageCreateData> getMessageCreateDataObjects(String message, List<Button> buttons) {
        return getMessageCreateDataObjects(message, null, buttons);
    }

    public static List<List<ActionRow>> getPartitionedButtonLists(List<Button> buttons) {
        List<List<ActionRow>> partitionedButtonRows = new ArrayList<>();
        try {
            buttons.removeIf(Objects::isNull);
        } catch (Exception e) {
            // Do nothing
        }
        if (buttons == null || buttons.isEmpty())
            return partitionedButtonRows;

        List<List<Button>> partitions = ListUtils.partition(buttons, 5);
        List<ActionRow> buttonRows = new ArrayList<>();
        for (List<Button> partition : partitions) {
            buttonRows.add(ActionRow.of(partition));
        }
        partitionedButtonRows = ListUtils.partition(buttonRows, 5);
        return partitionedButtonRows;
    }

    private static List<List<MessageEmbed>> getPartitionedEmbedLists(List<MessageEmbed> embeds) {
        if (embeds == null) {
            return new ArrayList<>();
        }
        if (embeds.isEmpty()) {
            return new ArrayList<>();
        }
        embeds = embeds.stream().filter(Objects::nonNull).collect(Collectors.toList());
        return ListUtils.partition(embeds, 8); //max is 10, but we've had issues with 6k char limit in embeds in single message
    }

    public static void sendMessageToThread(MessageChannel channel, String threadName, String messageToSend) {
        if (channel instanceof MessageChannelUnion union) {
            sendMessageToThread(union, threadName, messageToSend);
        } else {
            messageToSend = "Something went wrong trying to send this to a thread! Sorry!\n" + messageToSend;
            sendMessageToChannel(channel, messageToSend);
        }
    }

    public static void sendMessageToThread(MessageChannelUnion channel, String threadName, String messageToSend) {
        if (channel == null || threadName == null || messageToSend == null || threadName.isEmpty() || messageToSend.isEmpty())
            return;
        if (channel instanceof TextChannel) {
            ThreadArchiveHelper.checkThreadLimitAndArchive(channel.asGuildMessageChannel().getGuild());
            channel.asTextChannel().createThreadChannel(threadName)
                .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queueAfter(500, TimeUnit.MILLISECONDS,
                    t -> sendMessageToChannel(t, messageToSend));
        } else if (channel instanceof ThreadChannel) {
            sendMessageToChannel(channel, messageToSend);
        }
    }

    public static void sendMessageEmbedsToThread(MessageChannelUnion channel, String threadName, List<MessageEmbed> embeds) {
        if (channel == null || threadName == null || embeds == null || threadName.isEmpty() || embeds.isEmpty()) {
            return;
        }
        if (channel instanceof TextChannel) {
            ThreadArchiveHelper.checkThreadLimitAndArchive(channel.asGuildMessageChannel().getGuild());
            channel.asTextChannel().createThreadChannel(threadName)
                .setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
                .queueAfter(500, TimeUnit.MILLISECONDS,
                    t -> sendMessageToChannelWithEmbeds(t, null, embeds),
                    error -> BotLogger.error("Error creating thread channel: " + threadName + " in channel: " +
                        channel.getAsMention(), error));
        } else if (channel instanceof ThreadChannel) {
            sendMessageToChannelWithEmbeds(channel, null, embeds);
        }
    }

    public static void sendMessageEmbedsToCardsInfoThread(Player player, String message, List<MessageEmbed> embeds) {
        ThreadChannel channel = player.getCardsInfoThread();
        if (embeds == null || embeds.isEmpty()) {
            return;
        }
        sendMessageToChannelWithEmbeds(channel, message, embeds);
    }

    @Deprecated
    public static void sendMessageToBotLogWebhook(String message) {
        if (getBotLogWebhookURL() == null) {
            System.out.println("[BOT-LOG-WEBHOOK] " + message);
            return;
        }
        DiscordWebhook webhook = new DiscordWebhook(getBotLogWebhookURL());
        webhook.setContent(message);
        try {
            webhook.execute();
        } catch (Exception ignored) { System.out.println("[BOT-LOG-WEBHOOK] " + message + ignored.getMessage()); }
    }

    /**
     * @return a webhook URL for the bot-log channel of the Primary guild. Add
     *         your test server's ID and #bot-log channel webhook url here
     */
    @Deprecated
    public static String getBotLogWebhookURL() {
        return switch (AsyncTI4DiscordBot.guildPrimaryID) {
            case Constants.ASYNCTI4_HUB_SERVER_ID -> // AsyncTI4 Primary HUB Production Server
                "https://discord.com/api/webhooks/1106562763708432444/AK5E_Nx3Jg_JaTvy7ZSY7MRAJBoIyJG8UKZ5SpQKizYsXr57h_VIF3YJlmeNAtuKFe5v";
            case "1059645656295292968" -> // PrisonerOne's Test Server
                "https://discord.com/api/webhooks/1159478386998116412/NiyxcE-6TVkSH0ACNpEhwbbEdIBrvTWboZBTwuooVfz5n4KccGa_HRWTbCcOy7ivZuEp";
            default -> null;
        };
    }

    private static List<Button> sanitizeButtons(List<Button> buttons, MessageChannel channel) {
        if (buttons == null) return null;
        List<Button> newButtons = new ArrayList<>();
        List<String> goodButtonIDs = new ArrayList<>();
        List<String> badButtonIDsAndReason = new ArrayList<>();
        for (Button button : buttons) {
            if (button == null)
                continue;
            if (button.getId() == null && button.getStyle() != ButtonStyle.LINK)
                continue;

            // REMOVE DUPLICATE IDs
            if (goodButtonIDs.contains(button.getId())) {
                badButtonIDsAndReason.add(
                    "Button:  " + button.getId() + "\n Label:  " + button.getLabel() + "\n Error:  Duplicate ID");
                continue;
            }
            goodButtonIDs.add(button.getId());

            // REMOVE EMOJIS IF BOT CAN'T SEE IT
            if (button.getEmoji() instanceof CustomEmoji emoji && !ApplicationEmojiService.isValidAppEmoji(emoji) && AsyncTI4DiscordBot.jda.getEmojiById(emoji.getId()) == null) {
                String label = button.getLabel();
                if (label.isBlank()) {
                    label = String.format(":%s:", emoji.getName());
                }
                badButtonIDsAndReason.add("Button:  " + ButtonHelper.getButtonRepresentation(button) + "\n Error:  Emoji Not Found in Cache: " + emoji.getName() + " " + emoji.getId());
                button = Button.of(button.getStyle(), button.getId(), label);
            }
            if (button.getEmoji() instanceof UnicodeEmoji emoji && StringUtils.countMatches(emoji.getAsCodepoints(), "+") > 4) { //TODO: something better than (plus_sign_count > 4)
                String label = button.getLabel();
                if (label.isBlank()) {
                    label = String.format(":%s:", emoji.getName());
                }
                badButtonIDsAndReason.add("Button:  " + ButtonHelper.getButtonRepresentation(button) + "\n Error:  Bad Unicode Emoji: " + emoji.getName());
                button = Button.of(button.getStyle(), button.getId(), label);
            }
            newButtons.add(button);
        }

        // REPORT BAD BUTTONS
        if (!badButtonIDsAndReason.isEmpty()) {
            StringBuilder sb = new StringBuilder(channel.getAsMention());
            sb.append(" Bad Buttons detected and sanitized:\n");
            for (String error : badButtonIDsAndReason) {
                sb.append("```\n");
                sb.append(error);
                sb.append("\n```");
            }
            BotLogger.warning(sb.toString());
        }
        return newButtons;
    }

    private static String injectRules(String message) {
        if (message == null) {
            return null;
        }
        try {
            StringBuilder edited = new StringBuilder(message);
            StringBuilder copy = new StringBuilder(message.toLowerCase());
            for (String keyWord : AliasHandler.getInjectedRules()) {
                if (keyWord.equals("bombardment") && message.contains("Tactical Bombardment")) continue;
                if (keyWord.equals("production") && message.contains("Monopolize Production")) continue;
                if (copy.indexOf(keyWord) > -1) {
                    String replace = "](https://www.tirules.com/" + AliasHandler.getInjectedRule(keyWord) + ")";
                    int firstIndex = copy.indexOf(keyWord);
                    int lastIndex = firstIndex + keyWord.length() + 1;
                    copy.insert(firstIndex, "[");
                    copy.insert(lastIndex, replace);
                    edited.insert(firstIndex, "[");
                    edited.insert(lastIndex, replace);
                }
            }
            return edited.toString();
        } catch (Exception e) {
            BotLogger.error("Issue injecting Rules into message: " + message, e);
            return message;
        }
    }
}
