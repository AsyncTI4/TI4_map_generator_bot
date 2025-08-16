package ti4.service.decks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class ShowActionCardsService {

    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        List<String> unplayedACs = Helper.unplayedACs(game);
        String title = game.getName() + " - Unplayed Action Cards";
        String actionCardString = ActionCardHelper.actionCardListCondensedNoIds(unplayedACs, title);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), actionCardString);
    }

    public static void showDiscard(Game game, GenericInteractionCreateEvent event, boolean showFullText) {
        StringBuilder sb = new StringBuilder();
        List<Map.Entry<String, Integer>> discards =
                game.getDiscardActionCards().entrySet().stream().toList();

        Button showFullTextButton = null;
        if (showFullText) {
            sb.append(actionCardListFullText(discards, "Action card discard list"));
        } else {
            sb.append(discardListCondensed(discards, "Action card discard list"));
            showFullTextButton = Buttons.green("ACShowDiscardFullText", "Show Full Text");
        }
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), sb.toString(), showFullTextButton);
    }

    private static String actionCardListFullText(List<Map.Entry<String, Integer>> discards, String title) {
        // Set up the entry list
        List<Map.Entry<String, Integer>> aclist = new ArrayList<>(discards);
        Collections.reverse(aclist);
        Map<String, List<Map.Entry<String, Integer>>> cardsByName = new LinkedHashMap<>();
        aclist.forEach(ac -> {
            String name = Mapper.getActionCard(ac.getKey()).getName();
            if (!cardsByName.containsKey(name)) cardsByName.put(name, new ArrayList<>());
            cardsByName.get(name).addFirst(ac);
        });
        List<Map.Entry<String, List<Map.Entry<String, Integer>>>> entries = new ArrayList<>(cardsByName.entrySet());
        Collections.reverse(entries);

        // Build the string
        StringBuilder sb = new StringBuilder("**__").append(title).append(":__**");
        int index = 1;

        for (Map.Entry<String, List<Map.Entry<String, Integer>>> acEntryList : entries) {
            List<String> ids = acEntryList.getValue().stream()
                    .map(i -> "`(" + i.getValue() + ")`")
                    .toList();
            sb.append("\n").append(index).append("\\. ");
            index++;
            sb.append(CardEmojis.ActionCard.toString().repeat(ids.size()));
            sb.append(" _").append(acEntryList.getKey()).append("_ ");
            sb.append(String.join(", ", ids)).append("\n> ");
            sb.append(Mapper.getActionCard(acEntryList.getValue().getFirst().getKey())
                    .getRepresentationJustText());
        }
        return sb.toString();
    }

    private static String discardListCondensed(List<Map.Entry<String, Integer>> discards, String title) {
        // Set up the entry list
        List<Map.Entry<String, Integer>> aclist = new ArrayList<>(discards);
        Collections.reverse(aclist);
        Map<String, List<Map.Entry<String, Integer>>> cardsByName = new LinkedHashMap<>();
        aclist.forEach(ac -> {
            String name = Mapper.getActionCard(ac.getKey()).getName();
            if (!cardsByName.containsKey(name)) cardsByName.put(name, new ArrayList<>());
            cardsByName.get(name).addFirst(ac);
        });
        List<Map.Entry<String, List<Map.Entry<String, Integer>>>> entries = new ArrayList<>(cardsByName.entrySet());
        Collections.reverse(entries);

        // Build the string
        StringBuilder sb = new StringBuilder("__").append(title).append("__:");
        int index = 1;
        int pad = cardsByName.size() > 99 ? 4 : (cardsByName.size() > 9 ? 3 : 2);
        for (Map.Entry<String, List<Map.Entry<String, Integer>>> acEntryList : entries) {
            List<String> ids = acEntryList.getValue().stream()
                    .map(i -> "`(" + i.getValue() + ")`")
                    .toList();
            sb.append("\n").append(index).append("\\. ");
            index++;
            sb.append(CardEmojis.ActionCard.toString().repeat(ids.size()));
            sb.append(" _").append(acEntryList.getKey()).append("_");
            sb.append(String.join(", ", ids));
        }
        return sb.toString();
    }
}
