package ti4.service.decks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class ShowActionCardsService {

    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event) {
        showUnplayedACs(game, event, false);
    }

    public static void showUnplayedACs(Game game, GenericInteractionCreateEvent event, boolean showFullText) {
        List<String> unplayedACs = Helper.unplayedACs(game);
        String title = game.getName() + " - Unplayed Action Cards";
        String actionCardString = unplayedActionCardsText(unplayedACs, title, showFullText, game);
        MessageHelper.sendMessageToChannelWithButton(
                event.getMessageChannel(),
                actionCardString,
                showFullText ? null : Buttons.green("ACShowUnplayedFullText", "Show Full Text"));
    }

    public static void showDiscard(Game game, GenericInteractionCreateEvent event, boolean showFullText) {
        StringBuilder sb = new StringBuilder();

        String purgedText = getPurgedText(game, showFullText);
        if (purgedText != null) {
            sb.append(purgedText).append("\n\n");
        }

        String actionCardText = getActionCardDiscardPileText(game, showFullText);
        sb.append(actionCardText);

        String dataSkimmerText = getDataSkimmerDiscardText(game, showFullText);
        if (dataSkimmerText != null) {
            sb.append("\n\n").append(dataSkimmerText);
        }

        String garboziaText = getGarboziaDiscardText(game, showFullText);
        if (garboziaText != null) {
            sb.append("\n\n").append(garboziaText);
        }

        MessageHelper.sendMessageToChannelWithButton(
                event.getMessageChannel(),
                sb.toString(),
                showFullText ? null : Buttons.green("ACShowDiscardFullText", "Show Full Text"));
    }

    public static String getActionCardDiscardPileText(Game game, boolean showFullText) {
        List<Entry<String, Integer>> discards = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == null)
                .toList();
        String title = "Action card discard list";
        return acDiscardText(showFullText, discards, title, game);
    }

    public static String getPurgedText(Game game, boolean showFullText) {
        List<Entry<String, Integer>> purged = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == ACStatus.purged)
                .toList();
        if (!purged.isEmpty()) {
            String title = "Purged action cards";
            return acDiscardText(showFullText, purged, title, game);
        }
        return null;
    }

    public static String getDataSkimmerDiscardText(Game game, boolean showFullText) {
        List<Entry<String, Integer>> dataSkimmer = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == ACStatus.ralnelbt)
                .toList();
        if (!dataSkimmer.isEmpty()) {
            String title = "Action cards on " + FactionEmojis.Ralnel + " _Data Skimmer_";
            return acDiscardText(showFullText, dataSkimmer, title, game);
        }
        return null;
    }

    public static String getGarboziaDiscardText(Game game, boolean showFullText) {
        List<Entry<String, Integer>> garbozia = game.getDiscardActionCards().entrySet().stream()
                .filter(x -> game.getDiscardACStatus().get(x.getKey()) == ACStatus.garbozia)
                .toList();
        if (!garbozia.isEmpty()) {
            String title = "Action cards on " + MiscEmojis.LegendaryPlanet + " _Dok 'N Pic's Salvage Yard_";
            return acDiscardText(showFullText, garbozia, title, game);
        }
        return null;
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
            ActionCardModel model =
                    Mapper.getActionCard(acEntryList.getValue().getFirst().getKey());
            sb.append(model.getRepresentationJustText());
            if (model.getNotes() != null) {
                sb.append("\n> -# [").append(model.getNotes()).append("]");
            }
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

    private static String unplayedActionCardsText(
            List<String> unplayedACs, String title, boolean showFullText, Game game) {
        if (showFullText) return unplayedActionCardsFullText(unplayedACs, title, game);
        return ActionCardHelper.actionCardListCondensedNoIds(unplayedACs, title);
    }

    private static String unplayedActionCardsFullText(List<String> unplayedACs, String title, Game game) {
        Map<String, List<String>> cardsByName = new LinkedHashMap<>();
        unplayedACs.forEach(ac -> {
            if (Mapper.getActionCard(ac) != null) {
                String name = Mapper.getActionCard(ac).getName();
                cardsByName.computeIfAbsent(name, key -> new ArrayList<>()).addFirst(ac);
            } else {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Null action card with id `" + ac + "`.");
            }
        });

        List<Map.Entry<String, List<String>>> displayOrder = new ArrayList<>(cardsByName.entrySet());
        displayOrder.sort(Map.Entry.comparingByKey());

        StringBuilder sb = new StringBuilder("**__").append(title).append(":__**");
        int index = 1;
        for (Map.Entry<String, List<String>> entry : displayOrder) {
            sb.append("\n").append(index).append("\\. ");
            index++;
            sb.append(CardEmojis.ActionCard.toString().repeat(entry.getValue().size()));
            sb.append(" _").append(entry.getKey()).append("_\n> ");
            ActionCardModel model = Mapper.getActionCard(entry.getValue().getFirst());
            sb.append(model.getRepresentationJustText());
            if (model.getNotes() != null) {
                sb.append("\n> -# [").append(model.getNotes()).append("]");
            }
        }
        return sb.toString();
    }
}
