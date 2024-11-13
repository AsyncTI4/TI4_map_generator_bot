package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ShowDiscardActionCards extends GameStateSubcommand {

    public ShowDiscardActionCards() {
        super(Constants.SHOW_AC_DISCARD_LIST, "Show Action Card discard list", false, false);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_FULL_TEXT, "'true' to show full card text"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean showFullText = event.getOption(Constants.SHOW_FULL_TEXT, false, OptionMapping::getAsBoolean);
        showDiscard(game, event, showFullText);
    }

    public static void showDiscard(Game game, GenericInteractionCreateEvent event, boolean showFullText) {
        StringBuilder sb = new StringBuilder();
        List<Entry<String, Integer>> discards = game.getDiscardActionCards().entrySet().stream().toList();

        Button showFullTextButton = null;
        if (showFullText) {
            sb.append(actionCardListFullText(discards, "Action card discard list"));
        } else {
            sb.append(discardListCondensed(discards, "Action card discard list"));
            showFullTextButton = Buttons.green("ACShowDiscardFullText", "Show Full Text");
        }
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), sb.toString(), showFullTextButton);
    }

    @ButtonHandler("ACShowDiscardFullText")
    public static void showDiscardFullText(GenericInteractionCreateEvent event, Game game) {
        showDiscard(game, event, true);
    }

    public static String discardListCondensed(List<Entry<String, Integer>> discards, String title) {
        // Set up the entry list
        List<Entry<String, Integer>> aclist = new ArrayList<>(discards);
        Collections.reverse(aclist);
        Map<String, List<Entry<String, Integer>>> cardsByName = new LinkedHashMap<>();
        aclist.forEach(ac -> {
            String name = Mapper.getActionCard(ac.getKey()).getName();
            if (!cardsByName.containsKey(name)) cardsByName.put(name, new ArrayList<>());
            cardsByName.get(name).addFirst(ac);
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
            sb.append(Emojis.ActionCard.repeat(ids.size()));
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
        displayOrder.sort(Entry.comparingByKey());
        for (Entry<String, List<String>> acEntryList : displayOrder) {
            sb.append("\n").append(index).append(". ");
            sb.append(Emojis.ActionCard.repeat(acEntryList.getValue().size()));
            sb.append(" **").append(acEntryList.getKey()).append("**");
            // sb.append(Mapper.getActionCard()
            index++;
        }
        return sb.toString();
    }

    public static String actionCardListFullText(List<Entry<String, Integer>> discards, String title) {
        // Set up the entry list
        List<Entry<String, Integer>> aclist = new ArrayList<>(discards);
        Collections.reverse(aclist);
        Map<String, List<Entry<String, Integer>>> cardsByName = new LinkedHashMap<>();
        aclist.forEach(ac -> {
            String name = Mapper.getActionCard(ac.getKey()).getName();
            if (!cardsByName.containsKey(name)) cardsByName.put(name, new ArrayList<>());
            cardsByName.get(name).addFirst(ac);
        });
        List<Entry<String, List<Entry<String, Integer>>>> entries = new ArrayList<>(cardsByName.entrySet());
        Collections.reverse(entries);

        // Build the string
        StringBuilder sb = new StringBuilder("**__").append(title).append(":__**");
        int index = 1;

        for (Entry<String, List<Entry<String, Integer>>> acEntryList : entries) {
            List<String> ids = acEntryList.getValue().stream().map(i -> "`(" + i.getValue() + ")`").toList();
            sb.append("\n").append(index).append(". ");
            sb.append(Emojis.ActionCard.repeat(ids.size()));
            sb.append(" **").append(acEntryList.getKey()).append("** - ");
            sb.append(Mapper.getActionCard(acEntryList.getValue().getFirst().getKey()).getRepresentationJustText());
            index++;
        }
        return sb.toString();
    }
}
