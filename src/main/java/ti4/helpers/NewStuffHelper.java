package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.collections4.ListUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.service.emoji.CardEmojis;

public class NewStuffHelper {

    public static List<Button> buttonPagination(List<Button> allButtons, String prefixID, int pageNum) {
        return buttonPagination(allButtons, null, prefixID, 25, pageNum, false);
    }

    /**
     *
     * @param allButtons
     * @param prefixID
     * @param allottedSpace
     * @param pageNum
     * @return Page of buttons, with 2 additional "back" and "forward" buttons. Additional buttons are formatted like "prefixId_page1"
     */
    public static List<Button> buttonPagination(
            List<Button> allButtons,
            List<Button> extraButtons,
            String prefixID,
            int allottedSpace,
            int pageNum,
            boolean deleteButton) {
        if (deleteButton) allottedSpace--;
        if (extraButtons != null) allottedSpace -= extraButtons.size();

        if (allottedSpace < allButtons.size()) {
            if (allottedSpace < 3) {
                // This shouldn't ever happen as I don't really expect to ever see more than 7 other buttons,
                // which means allotted space should always be >= 18
                return Collections.emptyList();
            }
            List<List<Button>> paginated = ListUtils.partition(allButtons, allottedSpace - 2);
            int maxPage = paginated.size() - 1;
            if (pageNum < 0) pageNum = 0;
            if (pageNum > maxPage) pageNum = maxPage;

            List<Button> buttonsToUse = new ArrayList<>();
            if (pageNum > 0) {
                String prev = "Previous Page (" + pageNum + "/" + (maxPage + 1) + ")";
                Button prevPage = Buttons.blue(prefixID + "page" + (pageNum - 1), prev, "⏪");
                buttonsToUse.add(prevPage);
            }
            buttonsToUse.addAll(paginated.get(pageNum));
            if (pageNum < maxPage) {
                String next = "Next Page (" + (pageNum + 2) + "/" + (maxPage + 1) + ")";
                Button nextPage = Buttons.blue(prefixID + "page" + (pageNum + 1), next, "⏩");
                buttonsToUse.add(nextPage);
            }
            if (extraButtons != null) buttonsToUse.addAll(extraButtons);
            if (deleteButton) buttonsToUse.add(Buttons.red("deleteButtons", "Delete these buttons"));
            return buttonsToUse;
        }
        return allButtons;
    }

    public static boolean checkAndHandlePaginationChange(
            GenericInteractionCreateEvent event,
            MessageChannel channel,
            List<Button> allButtons,
            String message,
            String buttonPrefix,
            String buttonID) {
        return checkAndHandlePaginationChange(event, channel, allButtons, null, message, buttonPrefix, buttonID);
    }

    public static boolean checkAndHandlePaginationChange(
            GenericInteractionCreateEvent event,
            MessageChannel channel,
            List<Button> allButtons,
            List<Button> extraButtons,
            String message,
            String buttonPrefix,
            String buttonID) {
        String pageRegex = RegexHelper.pageRegex();
        Matcher pageMatcher = Pattern.compile(pageRegex).matcher(buttonID);
        if (pageMatcher.find()) {
            int page = Integer.parseInt(pageMatcher.group("page"));
            List<Button> buttons = buttonPagination(allButtons, extraButtons, buttonPrefix, 24, page, true);
            sendOrEditButtons(event, channel, message, buttons);
            return true;
        }
        return false;
    }

    public static void sendOrEditButtons(
            GenericInteractionCreateEvent event, MessageChannel channel, String message, List<Button> buttons) {
        if (event != null
                && event instanceof ButtonInteractionEvent bEvent
                && bEvent.getMessage().getContentRaw().equals(message)) {
            // replace the buttons in the previous message
            List<List<ActionRow>> actionRows = MessageHelper.getPartitionedButtonLists(buttons);
            if (!actionRows.isEmpty()) {
                bEvent.getHook()
                        .editOriginalComponents(actionRows.getFirst())
                        .queue(Consumers.nop(), BotLogger::catchRestError);
            }
        } else {
            // make a new message
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
    }

    @ButtonHandler("garbozia_")
    public static void resolveGarboziaTE(
            GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String buttonPrefix = player.getFinsFactionCheckerPrefix() + "garbozia_";
        String message = "Use buttons to pick an action card from the discard and put it on Doc 'N Pic's Salvage Yard:";
        List<Button> buttons = garboziaButtons(game, player);
        if (checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
            return; // no further handling necessary
        }

        Matcher pick =
                Pattern.compile("garbozia_pick" + RegexHelper.acRegex(game)).matcher(buttonID);
        if (pick.matches()) {
            String acNum = pick.group("ac");
            ActionCardModel acModel = Mapper.getActionCard(acNum);
            game.getDiscardACStatus().put(acNum, ACStatus.garbozia);

            String msg = player.getRepresentation() + " picked up " + acModel.getName()
                    + " from the discard and placed it on Doc 'N Pic's Salvage Yard.";
            msg +=
                    "\n> You can check the cards on garbozia at any time by looking at the Action Card discard pile in the bot map thread";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ActionCardHelper.sendActionCardInfo(game, player, event);
            ButtonHelper.deleteMessage(event);
        }
    }

    public static List<Button> garboziaButtons(Game game, Player player) {
        List<Button> allButtons = new ArrayList<>();
        Map<String, ACStatus> status = game.getDiscardACStatus();
        String pre = player.finChecker() + "garbozia_pick";

        // Right now, only a 'null' status allows the card to be picked up. Add more to this list if it changes in the
        // future
        List<ACStatus> allowedStatus = new ArrayList<>(Collections.singleton(null));
        game.getDiscardActionCards().keySet().stream()
                .filter(integer -> allowedStatus.contains(status.getOrDefault(integer, null)))
                .map(integer -> Map.entry(integer, Mapper.getActionCard(integer).getName()))
                .map(e -> Buttons.green(pre + e.getKey(), e.getValue(), CardEmojis.ActionCard))
                .forEach(allButtons::add);
        return allButtons;
    }
}
