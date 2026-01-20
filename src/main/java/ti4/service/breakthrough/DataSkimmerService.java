package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.ActionCardModel;
import ti4.service.decks.ShowActionCardsService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class DataSkimmerService {

    public static void fixDataSkimmer(Game game, Player ralnel) {
        if (!ralnel.hasUnlockedBreakthrough("ralnelbt")) return;
        String finChecker = ralnel.finChecker();

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(finChecker + "dataSkimmer_page0", "Use Data Skimmer", FactionEmojis.Ralnel));
        buttons.add(Buttons.red(
                finChecker + "discardDataSkimmer", "Discard Cards on Data Skimmer", CardEmojis.getACEmoji(game)));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);

        String message = "Use these buttons to interact with _Data Skimmer_.";
        if (!ralnel.isPassed()) {
            message =
                    "### __Warning!__ You are not passed, so you probably shouldn't be messing with _Data Skimmer_ yet.\n"
                            + message;
        }
        MessageHelper.sendMessageToChannelWithButtons(ralnel.getCorrectChannel(), message, buttons);
    }

    private static List<Button> getPickCardButtons(Game game, Player ralnel, String buttonPrefix) {
        List<Button> pickButtons = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<Entry<String, ActionCardModel>> cards = game.getDiscardACStatus().entrySet().stream()
                .filter(entry -> entry.getValue() == ACStatus.ralnelbt)
                .map(entry -> Map.entry(entry.getKey(), Mapper.getActionCard(entry.getKey())))
                .filter(entry -> names.add(entry.getValue().getName())) // filter duplicate names
                .sorted(Comparator.comparing(e -> e.getValue().getName()))
                .toList();
        cards.forEach(entry -> {
            String key = entry.getKey();
            ActionCardModel acModel = entry.getValue();
            int acNum = game.getDiscardActionCards().get(key);
            Button pick = Buttons.green(buttonPrefix + "draw" + acNum, acModel.getName(), CardEmojis.getACEmoji(game));
            pickButtons.add(pick);
        });
        return pickButtons;
    }

    @ButtonHandler("dataSkimmer_")
    private static void handleDataSkimmer(ButtonInteractionEvent event, Game game, Player ralnel, String buttonID) {
        String buttonPrefix = ralnel.finChecker() + "dataSkimmer_";
        List<Button> pickButtons = getPickCardButtons(game, ralnel, buttonPrefix);
        List<Button> peekButton = List.of(Buttons.gray("peekDataSkimmer", "See Cards on Data Skimmer", "👀"));

        String message = "Use these buttons to pick a card from _Data Skimmer_, to add to your hand.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                null, ralnel.getCorrectChannel(), pickButtons, peekButton, message, buttonPrefix, buttonID)) {
            ButtonHelper.deleteMessage(event);
            return; // no further handling necessary
        }

        String drawRegex = "dataSkimmer_draw" + RegexHelper.intRegex("ac");
        Matcher drawMatcher = Pattern.compile(drawRegex).matcher(buttonID);
        if (drawMatcher.matches()) {
            int acIndex = Integer.parseInt(drawMatcher.group("ac"));
            pickCardFromDiscard(game, ralnel, acIndex);
            discardCardsOnDataSkimmer(game, ralnel);
            ButtonHelper.deleteMessage(event);
        }
    }

    private static void pickCardFromDiscard(Game game, Player ralnel, int acNum) {
        String acID = game.getDiscardActionCards().entrySet().stream()
                .filter(entry -> entry.getValue() == acNum)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (game.pickActionCard(ralnel.getUserID(), acNum)) {
            ActionCardModel acModel = Mapper.getActionCard(acID);
            String msg = ralnel.getRepresentation() + " picked up _" + acModel.getName()
                    + "_ from _Data Skimmer_ and added it to their hand.";
            MessageHelper.sendMessageToChannel(ralnel.getCorrectChannel(), msg);
        } else {
            String msg = "Error: Could not pick up action card from _Data Skimmer_.";
            msg += "\nUse `/breakthrough activate` to try again.";
            MessageHelper.sendMessageToChannel(ralnel.getCorrectChannel(), msg);
        }
    }

    @ButtonHandler("discardDataSkimmer")
    public static void discardCardsOnDataSkimmer(Game game, Player ralnel) {
        if (!ralnel.hasUnlockedBreakthrough("ralnelbt")) return;

        List<String> discarded = new ArrayList<>();
        List<Entry<String, ACStatus>> pile =
                new ArrayList<>(game.getDiscardACStatus().entrySet());
        for (Entry<String, ACStatus> discard : pile) {
            if (discard.getValue() == ACStatus.ralnelbt) {
                discarded.add(discard.getKey());
                game.getDiscardACStatus().remove(discard.getKey());
            }
        }
        ActionCardHelper.serveReverseEngineerButtons(game, ralnel, discarded);
    }

    @ButtonHandler(value = "peekDataSkimmer", save = false)
    public static void peekDataSkimmer(ButtonInteractionEvent event, Game game) {
        String dataSkimmerText = ShowActionCardsService.getDataSkimmerDiscardText(game, true);
        List<String> splits = MessageHelper.splitLargeText(dataSkimmerText, 2000);
        for (String split : splits) {
            event.getHook().sendMessage(split).setEphemeral(true).queue(null, BotLogger::catchRestError);
        }
    }
}
