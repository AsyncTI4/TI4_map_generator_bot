package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
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
import ti4.model.ActionCardModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class DataSkimmerService {

    @ButtonHandler("dataSkimmer_")
    private static void handleDataSkimmer(ButtonInteractionEvent event, Game game, Player ralnel, String buttonID) {
        String buttonPrefix = ralnel.getFinsFactionCheckerPrefix() + "dataSkimmer_";
        List<Button> pickButtons = new ArrayList<>();
        game.getDiscardACStatus().entrySet().forEach(discard -> {
            if (!discard.getValue().equals(ACStatus.ralnelbt)) return;
            int acNum = game.getDiscardActionCards().get(discard.getKey());
            ActionCardModel acModel = Mapper.getActionCard(discard.getKey());
            Button pick =
                    Buttons.green(buttonPrefix + "draw" + acNum, "Draw " + acModel.getName(), CardEmojis.ActionCard);
            pickButtons.add(pick);
        });

        String message = "Use buttons to pick a card from Data Skimmer to add to your hand";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, ralnel.getCorrectChannel(), pickButtons, message, buttonPrefix, buttonID)) {
            return; // no further handling necessary
        }

        String drawRegex = "dataSkimmer_draw" + RegexHelper.intRegex("ac");
        Matcher drawMatcher = Pattern.compile(drawRegex).matcher(buttonID);
        if (drawMatcher.matches()) {
            int acIndex = Integer.parseInt(drawMatcher.group("ac"));
            ActionCardHelper.getActionCardFromDiscard(event, game, ralnel, acIndex);
            discardCardsOnDataSkimmer(game, ralnel);
            ButtonHelper.deleteMessage(event);
        }
    }

    public static void discardCardsOnDataSkimmer(Game game, Player ralnel) {
        if (!ralnel.hasUnlockedBreakthrough("ralnelbt")) return;

        List<String> discarded = new ArrayList<>();
        List<Entry<String, ACStatus>> pile =
                new ArrayList<>(game.getDiscardACStatus().entrySet());
        for (Entry<String, ACStatus> discard : pile) {
            if (discard.getValue().equals(ACStatus.ralnelbt)) {
                discarded.add(discard.getKey());
                game.getDiscardACStatus().remove(discard.getKey());
            }
        }
        ActionCardHelper.serveReverseEngineerButtons(game, ralnel, discarded);
    }
}
