package ti4.service.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageHistory.MessageRetrieveAction;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.MessageHelper.MessageFunction;
import ti4.message.logging.BotLogger;
import ti4.spring.jda.JdaService;

@UtilityClass
public class PublicDraftInfoService {
    private static final String SUMMARY_START = "# **__Draft Picks So Far__**:";

    public static void send(
            DraftManager draftManager,
            List<String> playerOrder,
            String currentPlayer,
            String nextPlayer,
            List<Button> extraButtons) {

        Game game = draftManager.getGame();
        MessageChannel channel = game.getMainGameChannel();
        if (channel == null) return;

        List<String> clearOldAttachments = new ArrayList<>();

        for (Draftable d : draftManager.getDraftables()) {
            String uniqueKey = game.getName() + "_" + d.getType().toString().toLowerCase();
            FileUpload uploadedImage = d.generateSummaryImage(draftManager, uniqueKey, null);
            if (uploadedImage != null) {
                clearOldAttachments.add(uniqueKey);
                MessageHelper.sendFileUploadToChannel(channel, uploadedImage);
            }
        }

        String draftSummary = getSummary(draftManager, playerOrder, currentPlayer, nextPlayer);
        MessageHelper.sendMessageToChannel(channel, draftSummary);

        List<String> clearOldHeaders = new ArrayList<>();

        for (Draftable d : draftManager.getDraftables()) {
            String draftableHeader = getSectionHeader(d.getDisplayName());
            List<Button> buttons = new ArrayList<>(getDraftButtons(draftManager, d));

            clearOldHeaders.add(draftableHeader);
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, draftableHeader, buttons);
        }

        // Clean up old messages, buttons, images, pings
        // Ping the current player
        pingCurrentPlayer(draftManager, currentPlayer, clearOldHeaders, clearOldAttachments, extraButtons);
    }

    public static void edit(
            GenericInteractionCreateEvent event,
            DraftManager draftManager,
            List<String> playerOrder,
            String currentPlayer,
            String nextPlayer,
            DraftableType draftableType) {

        Game game = draftManager.getGame();
        MessageChannel channel = game.getMainGameChannel();
        if (channel == null) return;

        String draftSummary = getSummary(draftManager, playerOrder, currentPlayer, nextPlayer);

        getMessageHistory(event, channel)
                .queue(editDraftInfo(draftManager, draftableType, draftSummary), BotLogger::catchRestError);
    }

    public static void pingCurrentPlayer(
            DraftManager draftManager,
            String currentPlayerUserID,
            List<String> clearMessageHeaders,
            List<String> clearAttachments,
            List<Button> extraButtons) {
        Game game = draftManager.getGame();
        String msg = "Nobody is up to draft...";
        Player p = game.getPlayer(currentPlayerUserID);
        if (p != null) msg = "### " + p.getPing() + " is up to draft!";

        List<Button> buttons = new ArrayList<>(extraButtons != null ? extraButtons : List.of());
        buttons = MessageHelper.addUndoButtonToList(buttons, game.getName());

        MessageChannel channel = game.getMainGameChannel();
        if (channel == null) return;
        MessageFunction clearOldFunc = clearOldPingsAndButtonsFunc(true, clearMessageHeaders, clearAttachments);
        MessageHelper.splitAndSentWithAction(msg, channel, buttons, clearOldFunc);
    }

    // Produce button message

    private List<Button> getDraftButtons(DraftManager draftManager, Draftable draftable) {
        List<DraftChoice> allDraftChoices = draftable.getAllDraftChoices();
        List<Button> buttons = new ArrayList<>();
        for (DraftChoice choice : allDraftChoices) {
            // Skip this choice if someone already has it.
            if (draftManager
                            .getPlayersWithChoiceKey(draftable.getType(), choice.getChoiceKey())
                            .size()
                    > 0) {
                continue;
            }

            buttons.add(choice.getButton());
        }

        // Append custom buttons
        buttons.addAll(draftable.getCustomChoiceButtons(null));

        return buttons;
    }

    // Summary generation

    private static String getSummary(
            DraftManager draftManager, List<String> playerOrder, String currentPlayer, String nextPlayer) {
        Game game = draftManager.getGame();
        List<Draftable> draftables = draftManager.getDraftables();
        int padding = String.format("%s", playerOrder.size()).length() + 1;

        Map<DraftableType, DraftChoice> defaultChoices = draftables.stream()
                .collect(HashMap::new, (m, d) -> m.put(d.getType(), d.getNothingPickedChoice()), Map::putAll);

        StringBuilder sb = new StringBuilder(SUMMARY_START);
        int pickNum = 1;
        for (String userId : playerOrder) {
            Player player = game.getPlayer(userId);
            PlayerDraftState picks = draftManager.getPlayerStates().get(userId);
            if (player == null || picks == null)
                throw new IllegalStateException("Player or picks missing for playerID " + userId);

            sb.append("\n> `").append(Helper.leftpad(pickNum + ".", padding)).append("` ");
            StringBuilder bulletSummary = new StringBuilder();
            for (Draftable draftable : draftables) {
                List<String> longChoiceNames = new ArrayList<>();
                if (picks.getPicks().containsKey(draftable.getType())) {
                    List<DraftChoice> draftablePicks = picks.getPicks().get(draftable.getType());
                    for (DraftChoice choice : draftablePicks) {
                        if (choice.getIdentifyingEmoji() != null) {
                            sb.append(choice.getIdentifyingEmoji());
                        } else {
                            longChoiceNames.add(choice.getDisplayName());
                        }
                    }
                } else if (defaultChoices.containsKey(draftable.getType())) {
                    DraftChoice noChoice = defaultChoices.get(draftable.getType());
                    if (noChoice.getIdentifyingEmoji() != null) {
                        sb.append(noChoice.getIdentifyingEmoji());
                    }
                    // Skip adding anything if no default emoji
                }

                if (longChoiceNames.size() > 0) {
                    bulletSummary.append("- " + draftable.getDisplayName() + ": " + System.lineSeparator() + "  - ");
                    bulletSummary.append(String.join(System.lineSeparator() + "  - ", longChoiceNames));
                }
            }

            if (nextPlayer != null && userId.equals(nextPlayer)) sb.append("*");
            if (currentPlayer != null && userId.equals(currentPlayer)) sb.append("**__");
            sb.append(player.getUserName());
            if (currentPlayer != null && userId.equals(currentPlayer)) sb.append("   <- CURRENTLY DRAFTING");
            if (nextPlayer != null && userId.equals(nextPlayer)) sb.append("   <- on deck");
            if (currentPlayer != null && userId.equals(currentPlayer)) sb.append("__**");
            if (nextPlayer != null && userId.equals(nextPlayer)) sb.append("*");

            pickNum++;
        }
        return sb.toString();
    }

    private static String getSectionHeader(String displayName) {
        return "__**" + displayName.toUpperCase() + ":**__";
    }

    // Edit previous messages

    private static MessageRetrieveAction getMessageHistory(
            GenericInteractionCreateEvent event, MessageChannel channel) {
        if (event != null && event.getMessageChannel() == channel && event instanceof ButtonInteractionEvent bEvent) {
            return channel.getHistoryAround(bEvent.getMessage(), 15);
        }
        return channel.getHistoryAround(channel.getLatestMessageIdLong(), 100);
    }

    private Consumer<MessageHistory> editDraftInfo(
            DraftManager draftManager, DraftableType draftableType, String newSummary) {
        Draftable draftable = draftManager.getDraftableByType(draftableType);
        if (draftable == null) {
            throw new IllegalArgumentException("No draftable of type " + draftableType + " found");
        }
        Predicate<String> isDraftableType = txt -> {
            String header = getSectionHeader(draftable.getDisplayName());
            return txt.equals(header);
        };
        List<Button> draftableButtons = new ArrayList<>(getDraftButtons(draftManager, draftable));

        // Images are good place for representing several elements of the draft state.
        // Assume they're not siloed to any draftable, and always try to do them all.
        Map<String, FileUpload> updateImageKeys = new HashMap<>();
        for (Draftable d : draftManager.getDraftables()) {
            String key = draftManager.getGame().getName() + "_"
                    + d.getType().toString().toLowerCase();
            FileUpload fileUpload = d.generateSummaryImage(draftManager, key, null);
            if (fileUpload != null) {
                updateImageKeys.put(key, fileUpload);
            }
        }

        return hist -> {
            boolean summaryDone = false, categoryDone = false;
            for (Message msg : hist.getRetrievedHistory()) {
                if (!msg.getAuthor().getId().equals(JdaService.getBotId())) continue;
                String txt = msg.getContentRaw();

                if (!summaryDone && txt.startsWith(SUMMARY_START)) {
                    summaryDone = true;
                    editMessage(msg, newSummary, null);
                }

                if (!categoryDone && isDraftableType.test(txt)) {
                    categoryDone = true;
                    editMessage(msg, null, draftableButtons);
                }

                if (!updateImageKeys.isEmpty()) {
                    for (Attachment atch : msg.getAttachments()) {
                        String keyDone = null;
                        for (Map.Entry<String, FileUpload> entry : updateImageKeys.entrySet()) {
                            String uniqueKey = entry.getKey();
                            if (atch.getFileName().contains(uniqueKey)) {
                                keyDone = uniqueKey;
                                FileUpload draftableImage = entry.getValue();
                                msg.editMessageAttachments(draftableImage).queue();
                                break;
                            }
                        }
                        if (keyDone != null) {
                            updateImageKeys.remove(keyDone);
                        }
                    }
                }
            }
        };
    }

    private void editMessage(Message msg, String newMessage, List<Button> newButtons) {
        List<MessageTopLevelComponent> newComponents = new ArrayList<>();
        if (newButtons != null) {
            List<List<Button>> partitioned = new ArrayList<>(ListUtils.partition(newButtons, 5));
            List<ActionRow> newRows = partitioned.stream().map(ActionRow::of).toList();
            newComponents.addAll(newRows);
        }

        if (newMessage != null && newButtons != null)
            msg.editMessage(newMessage).setComponents(newComponents).queue(Consumers.nop(), BotLogger::catchRestError);
        else if (newMessage != null) msg.editMessage(newMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        else if (newButtons != null)
            msg.editMessageComponents(newComponents).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    // Clear previous

    /**
     * This method is assumed to ONLY run as a callback on player ping. Therefore,
     * all found pings will be removed
     */
    private void clearHistMessages(
            MessageHistory hist,
            boolean clearFirstPing,
            List<String> clearMessageHeaders,
            List<String> clearAttachments) {
        boolean removePings = clearFirstPing;
        HashSet<String> removeHeaders = new HashSet<>(clearMessageHeaders != null ? clearMessageHeaders : List.of());
        HashSet<String> removeAttachments = new HashSet<>(clearAttachments != null ? clearAttachments : List.of());
        HashSet<String> seenHeader = new HashSet<>();
        HashSet<String> seenAttachment = new HashSet<>();
        for (Message msg : hist.getRetrievedHistory()) {
            String msgTxt = msg.getContentRaw();
            if (msgTxt.contains("is up to draft")) {
                if (removePings) msg.delete().queue();
                removePings = true;
            }

            for (String header : removeHeaders) {
                if (msgTxt.startsWith(header)) {
                    if (seenHeader.contains(header)) {
                        msg.delete().queue();
                    } else {
                        seenHeader.add(header);
                    }
                    break;
                }
            }

            if (msgTxt.contains(SUMMARY_START)) {
                if (seenHeader.contains(SUMMARY_START)) {
                    msg.delete().queue();
                } else {
                    seenHeader.add(SUMMARY_START);
                }
            }

            for (Attachment atch : msg.getAttachments()) {
                for (String attachName : removeAttachments) {
                    if (atch.getFileName().contains(attachName)) {
                        if (seenAttachment.contains(attachName)) {
                            msg.delete().queue();
                        } else {
                            seenAttachment.add(attachName);
                        }
                        break;
                    }
                }
            }
        }
    }

    private MessageFunction clearOldPingsAndButtonsFunc(
            boolean clearFirstPing, List<String> clearMessageHeaders, List<String> clearAttachments) {
        return msg -> msg.getChannel()
                .getHistoryBefore(msg, 100)
                .queue(
                        hist -> clearHistMessages(hist, clearFirstPing, clearMessageHeaders, clearAttachments),
                        BotLogger::catchRestError);
    }
}
