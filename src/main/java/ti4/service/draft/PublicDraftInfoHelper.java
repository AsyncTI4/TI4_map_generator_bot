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
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.MessageHelper.MessageFunction;
import ti4.message.logging.BotLogger;

@UtilityClass
public class PublicDraftInfoHelper {
    // ui things...for snake draft, in order!
    // generate and post and UPDATE slice image (requires slice draftable, template,
    // and displays player name+faction emoji)
    // uses...
    // - slices to get tile info and positioning, planet holders, slice name, etc.
    // - player that drafted it for their name, and potentially their faction name
    // w/ emoji too
    // -
    // generate and post and UPDATE summary (includes all draftables, player order
    // (orchestrator-specific), all choices made)
    // uses...
    // - highly specific to orchestrator what is public, how to order it, etc.
    // - snake: orchestrator order of players, who's drafting, who's on deck
    // - snake: what draftable choices are assigned to each player, by emoji
    // generate and post and UPDATE buttons (specific draftables, who sees what
    // buttons is orchestrator-specific)
    // - highly specific to orchestrator WHERE the buttons go and which are
    // available
    // - snake: requires remaining draftable choices, to produce buttons labeled
    // with text and/or emoji (faction: both, slice: emoji, position: emoji)

    // ULTIMATELY, where to locate different rendering functions?
    // - Slices: Special helper to accompany Summarizer. Functionally another
    // summarizer, specific to the slices of the draft.
    // - Summarizer: Orchestrator responsibility, on draft start and on every
    // choice. May not need special methods...
    // - Buttons: Draftables provide summary emojis/text, Draftables provide
    // remaining choices as button text tied to button key to handle choice.
    // Orchestrator delivers buttons associated with each choice as needed, and uses
    // summary info as needed.

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

        // MiltyDraftHelper.generateAndPostSlices(game);
        for (Draftable d : draftManager.getDraftables()) {
            FileUpload uploadedImage = d.generateDraftImage(draftManager);
            if (uploadedImage != null) {
                clearOldAttachments.add(uploadedImage.getName());
                MessageHelper.sendFileUploadToChannel(channel, uploadedImage);
            }
        }

        String draftSummary = draftManager.canInlineSummary()
                ? getInlineSummary(draftManager, playerOrder, currentPlayer, nextPlayer)
                : getBulletPointSummary(draftManager, playerOrder, currentPlayer, nextPlayer);
        MessageHelper.sendMessageToChannel(channel, draftSummary);

        List<String> clearOldHeaders = new ArrayList<>();

        for (Draftable d : draftManager.getDraftables()) {
            String draftableHeader = d.getChoiceHeader();
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

        String draftSummary = draftManager.canInlineSummary()
                ? getInlineSummary(draftManager, playerOrder, currentPlayer, nextPlayer)
                : getBulletPointSummary(draftManager, playerOrder, currentPlayer, nextPlayer);

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

        // List<Button> buttons = new ArrayList<>();
        // buttons.add(Buttons.gray("showMiltyDraft", "Show draft again"));
        // buttons.add(Buttons.blue("miltyFactionInfo_remaining", "Remaining faction info"));
        // buttons.add(Buttons.blue("miltyFactionInfo_picked", "Picked faction info"));
        // buttons.add(Buttons.blue("miltyFactionInfo_all", "All faction info"));
        List<Button> buttons = new ArrayList<>(extraButtons != null ? extraButtons : List.of());
        buttons = MessageHelper.addUndoButtonToList(buttons, game.getName());

        MessageChannel channel = game.getMainGameChannel();
        if (channel == null) return;
        MessageFunction clearOldFunc = clearOldPingsAndButtonsFunc(true, clearMessageHeaders, clearAttachments);
        MessageHelper.splitAndSentWithAction(msg, channel, buttons, clearOldFunc);
    }

    /*
     * Where does the game channel come from? The event object, and the game object.
     * Do we want to have a concept of the game at this level? Or should it just be
     * passed in whenever something is needed?
     * Life is simpler with the Game object.
     */

    // Produce button message

    private List<Button> getDraftButtons(DraftManager draftManager, Draftable draftable) {
        List<DraftChoice> allDraftChoices = draftable.getAllDraftChoices();
        List<Button> buttons = new ArrayList<>();
        for (DraftChoice choice : allDraftChoices) {
            // Skip this choice if someone already has it.
            if (draftManager.getPlayerStates().values().stream()
                    .anyMatch(pState -> pState.getPicks().containsKey(choice.getType())
                            && pState.getPicks().get(choice.getType()).contains(choice))) {
                continue;
            }

            String buttonText = choice.getButtonText();
            // If there's any writing on the button, use a gray background for readability.
            boolean grayButton = buttonText.matches(".*[a-zA-Z]+.*");
            Button button = grayButton
                    ? Buttons.gray(draftable.getButtonPrefix() + "_" + choice.getButtonSuffix(), choice.getButtonText())
                    : Buttons.green(
                            draftable.getButtonPrefix() + "_" + choice.getButtonSuffix(), choice.getButtonText());
            buttons.add(button);
        }

        // Append custom buttons
        buttons.addAll(draftable.getCustomButtons());

        return buttons;
    }

    // Summary generation

    private static String getInlineSummary(
            DraftManager draftManager, List<String> playerOrder, String currentPlayer, String nextPlayer) {
        Game game = draftManager.getGame();
        List<Draftable> draftables = draftManager.getDraftables();
        int padding = String.format("%s", playerOrder.size()).length() + 1;

        Map<DraftableType, String> defaultSummaries = draftables.stream()
                .collect(HashMap::new, (m, d) -> m.put(d.getType(), d.getDefaultInlineSummary()), Map::putAll);

        StringBuilder sb = new StringBuilder(SUMMARY_START);
        int pickNum = 1;
        for (String userId : playerOrder) {
            Player player = game.getPlayer(userId);
            PlayerDraftState picks = draftManager.getPlayerStates().get(userId);
            if (player == null || picks == null)
                throw new IllegalStateException("Player or picks missing for playerID " + userId);

            sb.append("\n> `").append(Helper.leftpad(pickNum + ".", padding)).append("` ");
            for (Draftable draftable : draftables) {
                if (picks.getPicks().containsKey(draftable.getType())) {
                    List<DraftChoice> draftablePicks = picks.getPicks().get(draftable.getType());
                    for (DraftChoice choice : draftablePicks) {
                        sb.append(choice.getInlineSummary());
                    }
                } else if (defaultSummaries.containsKey(draftable.getType())) {
                    sb.append(defaultSummaries.get(draftable.getType()));
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

    // This kind of summary would be needed to support a public franken-draft.
    // Instead of returning a String, it should probably return a Map<UserId,
    // SummaryString>, so that each player's summary can be posted to a distinct
    // message, thereby avoiding length limits.
    // This would something like:
    // Draft Picks So Far: <PlayerName>
    // - **Faction Technology**
    // - Valefor Assimilator X
    // - Advanced Carrier 2
    // - **Blue Tiles**
    // - Rigels
    // etc.
    // The actual summary text could be the button text, and the parent list would
    // be the Draftable Type.
    private static String getBulletPointSummary(
            DraftManager draftManager, List<String> playerOrder, String currentPlayer, String nextPlayer) {
        throw new UnsupportedOperationException("Bullet point summary not implemented yet");
    }

    // Edit previous messages

    private static MessageRetrieveAction getMessageHistory(
            GenericInteractionCreateEvent event, MessageChannel channel) {
        if (event != null && event.getMessageChannel() == channel && event instanceof ButtonInteractionEvent bEvent) {
            return channel.getHistoryAround(bEvent.getMessage(), 10);
        }
        return channel.getHistoryAround(channel.getLatestMessageIdLong(), 100);
    }

    private Consumer<MessageHistory> editDraftInfo(
            DraftManager draftManager, DraftableType draftableType, String newSummary) {
        Draftable draftable = draftManager.getDraftables().stream()
                .filter(d -> d.getType() == draftableType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No draftable found for type " + draftableType));
        Predicate<String> isDraftableType = txt -> {
            String header = draftable.getChoiceHeader();
            return txt.equals(header);
        };
        List<Button> draftableButtons = new ArrayList<>(getDraftButtons(draftManager, draftable));
        FileUpload draftableImage = draftable.generateDraftImage(draftManager);

        return hist -> {
            boolean summaryDone = false, categoryDone = false, sliceImgDone = false;
            for (Message msg : hist.getRetrievedHistory()) {
                if (!msg.getAuthor().getId().equals(AsyncTI4DiscordBot.getBotId())) continue;
                String txt = msg.getContentRaw();

                if (!summaryDone && txt.startsWith(SUMMARY_START)) {
                    summaryDone = true;
                    editMessage(msg, newSummary, null);
                }

                if (!categoryDone && isDraftableType.test(txt)) {
                    categoryDone = true;
                    editMessage(msg, null, draftableButtons);
                }

                if (!sliceImgDone && draftableImage != null) {
                    for (Attachment atch : msg.getAttachments()) {
                        if (atch.getFileName().contains(draftableImage.getName())) {
                            sliceImgDone = true;
                            msg.editMessageAttachments(draftableImage).queue();
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

    /** This method is assumed to ONLY run as a callback on player ping. Therefore, all found pings will be removed */
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

            // if (clearOldDraftInfo && msgTxt.startsWith(SUMMARY_START)) {
            //     if (removeSummary) msg.delete().queue();
            //     removeSummary = true;
            // }
            // if (clearOldDraftInfo && msgTxt.equals(SLICES)) {
            //     if (removeSliceMsgs) msg.delete().queue();
            //     removeSliceMsgs = true;
            // }
            // if (clearOldDraftInfo && msgTxt.equals(FACTIONS)) {
            //     if (removeFactionMsgs) msg.delete().queue();
            //     removeFactionMsgs = true;
            // }
            // if (clearOldDraftInfo && msgTxt.equals(POSITION)) {
            //     if (removePositionMsgs) msg.delete().queue();
            //     removePositionMsgs = true;
            // }
            // if (clearOldDraftInfo && messageIsSliceImg(msg)) {
            //     if (removeImages) msg.delete().queue();
            //     removeImages = true;
            // }
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
