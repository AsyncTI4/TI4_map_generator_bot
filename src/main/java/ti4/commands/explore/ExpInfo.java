package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.MessageHelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.StringTokenizer;

public class ExpInfo extends ExploreSubcommandData {

    public ExpInfo() {
        super(Constants.INFO, "Display cards in exploration decks and discards.");
        addOptions(typeOption);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        ArrayList<String> types = new ArrayList<String>();
        OptionMapping reqType = event.getOption(Constants.TRAIT);
        if (reqType != null) {
            types.add(reqType.getAsString());
        } else {
            types.add(Constants.CULTURAL);
            types.add(Constants.INDUSTRIAL);
            types.add(Constants.HAZARDOUS);
            types.add(Constants.FRONTIER);
        }
        for (String currentType : types) {
            StringBuilder info = new StringBuilder();
            ArrayList<String> deck = activeMap.getExploreDeck(currentType);
            Collections.sort(deck);
            Integer deckCount = deck.size();
            Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
            NumberFormat formatPercent = NumberFormat.getPercentInstance();
            formatPercent.setMaximumFractionDigits(1);
            ArrayList<String> discard = activeMap.getExploreDiscard(currentType);
            Collections.sort(discard);
            Integer discardCount = discard.size();

            info.append(Helper.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK** (").append(String.valueOf(deckCount)).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            info.append(listNames(deck)).append("\n");
            info.append(Helper.getEmojiFromDiscord(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD** (").append(String.valueOf(discardCount)).append(")\n");
            info.append(listNames(discard)).append("\n_ _\n");
            sendMessage(info.toString());
        }
    }

    private String listNames(ArrayList<String> deck) {
        StringBuilder sb = new StringBuilder();
        for (String cardID : deck) {
            String card = Mapper.getExplore(cardID);
            String name = null;
            if (card != null) {
                StringTokenizer cardInfo = new StringTokenizer(card, ";");
                name = cardInfo.nextToken();
            }
            sb.append("(").append(cardID).append(") ").append(name).append("\n");
        }
        return sb.toString();
    }
}
