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
        List<Entry<String, Integer>> discards = game.getDiscardActionCards().entrySet().stream().toList();
        sb.append(discardListCondensed(discards, "Action card discard list"));

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
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append("**__").append(title).append(":__**");
        Map<String, List<String>> cardsByName = discards.stream()
            .collect(Collectors.groupingBy(ac -> Mapper.getActionCard(ac).getName()));
        int index = 1;
        int pad = cardsByName.size() > 99 ? 4 : (cardsByName.size() > 9 ? 3 : 2);

        List<Entry<String, List<String>>> displayOrder = new ArrayList<>(cardsByName.entrySet());
        displayOrder.sort(Comparator.comparing(e -> e.getKey()));
        for (Entry<String, List<String>> acEntryList : displayOrder) {
            sb.append("\n`").append(Helper.leftpad(index + ".", pad)).append("` - ");
            sb.append(StringUtils.repeat(Emojis.ActionCard, acEntryList.getValue().size()));
            sb.append(" **").append(acEntryList.getKey()).append("**");
            index++;
        }
        return sb.toString();
    }
}
