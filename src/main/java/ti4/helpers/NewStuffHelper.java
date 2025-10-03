package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
//import ti4.map.Game.ACStatus;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.service.emoji.CardEmojis;

public class NewStuffHelper {

    public static List<Button> buttonPagination(List<Button> allButtons, String prefixID, int pageNum) {
        return buttonPagination(allButtons, prefixID, 25, pageNum, false);
    }

    /**
     * 
     * @param allButtons
     * @param prefixID
     * @param allottedSpace
     * @param pageNum
     * @return Page of buttons, with 2 additional "back" and "forward" buttons. Additional buttons are formatted like "prefixId_page1"
     */
    public static List<Button> buttonPagination(List<Button> allButtons, String prefixID, int allottedSpace, int pageNum, boolean deleteButton) {
        if (deleteButton) allottedSpace--;

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
                Button prevPage = Button.of(ButtonStyle.PRIMARY, prefixID + "page" + (pageNum - 1), "Previous Page", Emoji.fromUnicode("⏪"));
                buttonsToUse.add(prevPage);
            }
            buttonsToUse.addAll(paginated.get(pageNum));
            if (pageNum < maxPage) {
                Button nextPage = Button.of(ButtonStyle.PRIMARY, prefixID + "page" + (pageNum + 1), "Next Page", Emoji.fromUnicode("⏩"));
                buttonsToUse.add(nextPage);
            }
            if (deleteButton) buttonsToUse.add(Buttons.red("deleteButtons", "Delete these buttons"));
            return buttonsToUse;
        }
        return allButtons;
    }

    public static boolean checkAndHandlePaginationChange(@NotNull GenericInteractionCreateEvent event, MessageChannel channel, List<Button> allButtons, String message, String buttonPrefix, String buttonID) {
        String pageRegex = RegexHelper.pageRegex();
        Matcher pageMatcher = Pattern.compile(pageRegex).matcher(buttonID);
        if (pageMatcher.find()) {
            int page = Integer.parseInt(pageMatcher.group("page"));
            List<Button> buttons = NewStuffHelper.buttonPagination(allButtons, buttonPrefix, 24, page, true);
            NewStuffHelper.sendOrEditButtons(event, event.getMessageChannel(), message, buttons);
            return true;
        }
        return false;
    }

    public static void sendOrEditButtons(GenericInteractionCreateEvent event, MessageChannel channel, String message, List<Button> buttons) {
        if (event != null && event instanceof ButtonInteractionEvent bEvent && bEvent.getMessage().getContentRaw().equals(message)) {
            // replace the buttons in the previous message
            List<List<ActionRow>> actionRows = MessageHelper.getPartitionedButtonLists(buttons);
            if (actionRows.size() >= 1) {
                bEvent.getHook().editOriginalComponents(actionRows.get(0)).queue();
            }
        } else {
            //make a new message
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
    }

    // @ButtonHandler("garbozia_")
    // public static void resolveGarboziaTE(GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
    //     String buttonPrefix = player.getFinsFactionCheckerPrefix() + "garbozia_";
    //     String message = "Use buttons to pick an action card from the discard and put it on Doc 'N Pic's Salvage Yard:";
    //     List<Button> buttons = garboziaButtons(game, player);
    //     if (checkAndHandlePaginationChange(event, player.getCorrectChannel(), buttons, message, buttonPrefix, buttonID)) {
    //         return; // no further handling necessary
    //     }

    //     Matcher pick = Pattern.compile("garbozia_pick" + RegexHelper.acRegex(game)).matcher(buttonID);
    //     if (pick.matches()) {
    //         String acNum = pick.group("ac");
    //         ActionCardModel acModel = Mapper.getActionCard(acNum);
    //         //game.getDiscardACStatus().put(acNum, ACStatus.garbozia);

    //         String msg = player.getRepresentation() + " picked up " + acModel.getName() + " from the discard and placed it on Doc 'N Pic's Salvage Yard.";
    //         msg += "\n> You can check the cards on garbozia at any time by looking at the Action Card discard pile in the bot map thread";
    //         MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    //         ActionCardHelper.sendActionCardInfo(game, player, event);
    //         ButtonHelper.deleteMessage(event);
    //         return;
    //     }
    // }

    // public static List<Button> garboziaButtons(Game game, Player player) {
    //     List<Button> allButtons = new ArrayList<>();
    //     Map<String, ACStatus> status = game.getDiscardACStatus();
    //     String prefixID = player.getFinsFactionCheckerPrefix() + "garbozia_";

    //     // Right now, none of the statuses allow the card to be picked up. Add more to this list if it changes in the future
    //     List<ACStatus> allowedStatus = new ArrayList<>(Collections.singleton(null));
    //     game.getDiscardActionCards().entrySet().stream()
    //         .filter(e -> allowedStatus.contains(status.getOrDefault(e.getKey(), null)))
    //         .map(e -> Buttons.green(prefixID + "pick" + e.getKey(), Mapper.getActionCard(e.getKey()).getName(), CardEmojis.ActionCard))
    //         .forEach(allButtons::add);
    //     return allButtons;
    // }
}
