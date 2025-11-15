package ti4.service.decks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;

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
        List<Entry<String, Integer>> discards = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == null)
                .toList();
        List<Entry<String, Integer>> purged = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == ACStatus.purged)
                .toList();
        List<Entry<String, Integer>> dataSkimmer = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == ACStatus.ralnelbt)
                .toList();
        List<Entry<String, Integer>> garbozia = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == ACStatus.garbozia)
                .toList();

        if (!purged.isEmpty()) {
            String title = "PURGED Action cards";
            sb.append(acDiscardText(showFullText, purged, title, game));
            sb.append("\n\n");
        }
        {
            String title = "Action card discard list";
            sb.append(acDiscardText(showFullText, discards, title, game));
        }
        if (!dataSkimmer.isEmpty()) {
            sb.append("\n\n");
            String title = "Action cards on " + FactionEmojis.Ralnel + " Data Skimmer";
            sb.append(acDiscardText(showFullText, dataSkimmer, title, game));
        }
        if (!garbozia.isEmpty()) {
            sb.append("\n\n");
            String title = "Action cards on " + MiscEmojis.LegendaryPlanet + " Garbozia";
            sb.append(acDiscardText(showFullText, garbozia, title, game));
        }

        Button showFullTextButton = showFullText ? Buttons.green("ACShowDiscardFullText", "Show Full Text") : null;
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), sb.toString(), showFullTextButton);
    }

    public static String acDiscardText(
            boolean fullText, List<Entry<String, Integer>> discards, String title, Game game) {
        if (fullText) return actionCardListFullText(discards, title, game);
        return discardListCondensed(discards, title);
    }

    private static String actionCardListFullText(List<Map.Entry<String, Integer>> discards, String title, Game game) {
        // Set up the entry list
        List<Map.Entry<String, Integer>> aclist = new ArrayList<>(discards);
        Collections.reverse(aclist);
        Map<String, List<Map.Entry<String, Integer>>> cardsByName = new LinkedHashMap<>();
        aclist.forEach(ac -> {
            if (Mapper.getActionCard(ac.getKey()) != null) {
                String name = Mapper.getActionCard(ac.getKey()).getName();
                if (!cardsByName.containsKey(name)) cardsByName.put(name, new ArrayList<>());
                cardsByName.get(name).addFirst(ac);
            } else {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(), "Null AC with id " + ac.getKey() + " " + ac.getValue());
            }
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
