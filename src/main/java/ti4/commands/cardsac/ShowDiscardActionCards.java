package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowDiscardActionCards extends ACCardsSubcommandData {
    public ShowDiscardActionCards() {
        super(Constants.SHOW_AC_DISCARD_LIST, "Show Action Card discard list");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        showDiscard(game, event);
    }

    public static void showDiscard(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Action card discard list: ").append("\n");
        int index = 1;
        for (Map.Entry<String, Integer> ac : game.getDiscardActionCards().entrySet()) {
            sb.append("`").append(index).append(".").append(Helper.leftpad("(" + ac.getValue(), 4)).append(")` - ");
            if (Mapper.getActionCard(ac.getKey()) != null) {
                sb.append(Mapper.getActionCard(ac.getKey()).getRepresentation());
            }

            index++;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static String discardListCondensed(List<Entry<String, Integer>> discards, String title) {
        // Set up the entry list
        List<Entry<String, Integer>> aclist = new ArrayList<>(discards);
        Collections.reverse(aclist);
        Map<String, List<Entry<String, Integer>>> cardsByName = new LinkedHashMap<>();
        aclist.forEach(ac -> {
            String name = Mapper.getActionCard(ac.getKey()).getName();
            if (!cardsByName.containsKey(name)) cardsByName.put(name, new ArrayList<>());
            cardsByName.get(name).add(0, ac);
        });
        List<Entry<String, List<Entry<String, Integer>>>> entries = new ArrayList<>(cardsByName.entrySet());
        Collections.reverse(entries);

        // Build the string
        StringBuilder sb = new StringBuilder("**__").append(title).append(":__**");
        int index = 1;
        int pad = cardsByName.size() > 99 ? 4 : (cardsByName.size() > 9 ? 3 : 2);
        for (Entry<String, List<Entry<String, Integer>>> acEntryList : entries) {
            List<String> ids = acEntryList.getValue().stream().map(i -> "`(" + i.getValue() + ")`").toList();
            sb.append("\n`").append(Helper.leftpad(index + ".", pad)).append("` - ");
            sb.append(StringUtils.repeat(Emojis.ActionCard, ids.size()));
            sb.append(" **").append(acEntryList.getKey()).append("**");
            sb.append(String.join(",", ids));
            index++;
        }
        return sb.toString();
    }

    public static String actionCardListCondensedNoIds(List<String> discards, String title) {
        // Sort the action cards by display name
        Map<String, List<String>> cardsByName = discards.stream().collect(Collectors.groupingBy(ac -> Mapper.getActionCard(ac).getName()));
        List<Entry<String, List<String>>> entries = new ArrayList<>(cardsByName.entrySet());
        Collections.sort(entries, Comparator.comparing(Entry::getKey));

        // Print the action cards, sorted by display name
        StringBuilder sb = new StringBuilder("**__").append(title).append(":__**");
        int index = 1, pad = cardsByName.size() > 99 ? 4 : (cardsByName.size() > 9 ? 3 : 2);
        for (Entry<String, List<String>> acEntryList : entries) {
            sb.append("\n`").append(Helper.leftpad(index + ".", pad)).append("` - ");
            sb.append(StringUtils.repeat(Emojis.ActionCard, acEntryList.getValue().size()));
            sb.append(" **").append(acEntryList.getKey()).append("**");
            index++;
        }
        return sb.toString();
    }
}