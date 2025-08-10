package ti4.service.milty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageHistory.MessageRetrieveAction;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.message.MessageHelper.MessageFunction;

@UtilityClass
public class DraftDisplayService {
    private static final String SLICES = "**__Slices:__**";
    private static final String FACTIONS = "**__Factions:__**";
    private static final String POSITION = "**__Speaker Order:__**";
    private static final String SUMMARY_START = "# **__Draft Picks So Far__**:";

    public void updateDraftInformation(
            GenericInteractionCreateEvent event, MiltyDraftManager manager, Game game, String category) {
        MessageChannel channel = game.getMainGameChannel();
        if (channel == null) return;

        getMessageHistory(event, channel).queue(editDraftInfo(manager, game, category), BotLogger::catchRestError);
    }

    public void repostDraftInformation(GenericInteractionCreateEvent event, MiltyDraftManager manager, Game game) {
        MessageChannel channel = game.getMainGameChannel();
        if (channel == null) return;

        String draftSummary = manager.getOverallSummaryString(game);
        MiltyDraftHelper.generateAndPostSlices(game);
        MessageHelper.sendMessageToChannel(channel, draftSummary);
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, SLICES, manager.getSliceButtons());
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, FACTIONS, manager.getFactionButtons());
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, POSITION, manager.getPositionButtons());
        pingCurrentDraftPlayer(event, manager, game, true);
    }

    public void pingCurrentDraftPlayer(
            GenericInteractionCreateEvent event, MiltyDraftManager manager, Game game, boolean clearOldButtons) {
        String msg = "Nobody is up to draft...";
        Player p = manager.getCurrentDraftPlayer(game);
        if (p != null) msg = "### " + p.getPing() + " is up to draft!";

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("showMiltyDraft", "Show draft again"));
        buttons.add(Buttons.blue("miltyFactionInfo_remaining", "Remaining faction info"));
        buttons.add(Buttons.blue("miltyFactionInfo_picked", "Picked faction info"));
        buttons.add(Buttons.blue("miltyFactionInfo_all", "All faction info"));
        buttons = MessageHelper.addUndoButtonToList(buttons, game.getName());

        MessageChannel channel = game.getMainGameChannel();
        if (channel == null) return;
        MessageFunction func = clearOldPingsAndButtonsFunc(true, clearOldButtons);
        MessageHelper.splitAndSentWithAction(msg, channel, buttons, func);
    }

    // -----------------------------------------------------------------------------------
    // Private Helper Functions
    // -----------------------------------------------------------------------------------

    private static MessageRetrieveAction getMessageHistory(
            GenericInteractionCreateEvent event, MessageChannel channel) {
        if (event != null && event.getMessageChannel() == channel && event instanceof ButtonInteractionEvent bEvent) {
            return channel.getHistoryAround(bEvent.getMessage(), 10);
        }
        return channel.getHistoryAround(channel.getLatestMessageIdLong(), 100);
    }

    private Consumer<MessageHistory> editDraftInfo(MiltyDraftManager manager, Game game, String category) {
        Predicate<String> isCategory = txt -> switch (category) {
            case "slice" -> txt.equals(SLICES);
            case "faction" -> txt.equals(FACTIONS);
            case "order" -> txt.equals(POSITION);
            default -> false;
        };
        List<Button> categoryButtons =
                switch (category) {
                    case "slice" -> manager.getSliceButtons();
                    case "faction" -> manager.getFactionButtons();
                    case "order" -> manager.getPositionButtons();
                    default -> List.of();
                };
        String newSummary = manager.getOverallSummaryString(game);
        return hist -> {
            boolean summaryDone = false, categoryDone = false, sliceImgDone = false;
            for (Message msg : hist.getRetrievedHistory()) {
                if (!msg.getAuthor().getId().equals(AsyncTI4DiscordBot.getBotId())) continue;
                String txt = msg.getContentRaw();

                if (!summaryDone && txt.startsWith(SUMMARY_START)) {
                    summaryDone = true;
                    editMessage(game, msg, newSummary, null);
                }

                if (!categoryDone && isCategory.test(txt)) {
                    categoryDone = true;
                    editMessage(game, msg, null, categoryButtons);
                }

                if (!sliceImgDone && messageIsSliceImg(msg)) {
                    sliceImgDone = true;
                    FileUpload newImg = MiltyDraftHelper.generateImage(game);
                    msg.editMessageAttachments(newImg).queue();
                }
            }
        };
    }

    private void editMessage(Game game, Message msg, String newMessage, List<Button> newButtons) {
        List<LayoutComponent> newComponents = new ArrayList<>();
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

    private static boolean messageIsSliceImg(Message m) {
        if (!m.getAttachments().isEmpty()) {
            for (Attachment atch : m.getAttachments()) {
                if (atch.getFileName().contains("_miltydraft")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** This method is assumed to ONLY run as a callback on player ping. Therefore, all found pings will be removed */
    private void clearHistMessages(MessageHistory hist, boolean clearFirstPing, boolean clearOldDraftInfo) {
        boolean removePings = clearFirstPing;
        boolean removeImages = false;
        boolean removeSummary = false;
        boolean removeSliceMsgs = false;
        boolean removeFactionMsgs = false;
        boolean removePositionMsgs = false;
        for (Message msg : hist.getRetrievedHistory()) {
            String msgTxt = msg.getContentRaw();
            if (msgTxt.contains("is up to draft")) {
                if (removePings) msg.delete().queue();
                removePings = true;
            }

            if (clearOldDraftInfo && msgTxt.startsWith(SUMMARY_START)) {
                if (removeSummary) msg.delete().queue();
                removeSummary = true;
            }
            if (clearOldDraftInfo && msgTxt.equals(SLICES)) {
                if (removeSliceMsgs) msg.delete().queue();
                removeSliceMsgs = true;
            }
            if (clearOldDraftInfo && msgTxt.equals(FACTIONS)) {
                if (removeFactionMsgs) msg.delete().queue();
                removeFactionMsgs = true;
            }
            if (clearOldDraftInfo && msgTxt.equals(POSITION)) {
                if (removePositionMsgs) msg.delete().queue();
                removePositionMsgs = true;
            }
            if (clearOldDraftInfo && messageIsSliceImg(msg)) {
                if (removeImages) msg.delete().queue();
                removeImages = true;
            }
        }
    }

    private MessageFunction clearOldPingsAndButtonsFunc(boolean clearFirstPing, boolean clearOldDraftInfo) {
        return msg -> msg.getChannel()
                .getHistoryBefore(msg, 100)
                .queue(hist -> clearHistMessages(hist, clearFirstPing, clearOldDraftInfo), BotLogger::catchRestError);
    }
}
