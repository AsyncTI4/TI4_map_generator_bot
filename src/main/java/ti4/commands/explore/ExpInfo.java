package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

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
        OptionMapping reqType = event.getOption(Constants.EXPLORE_TYPE);
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
            ArrayList<String> discard = activeMap.getExploreDiscard(currentType);
            Collections.sort(discard);
            info.append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK**\n").append(listNames(deck)).append("\n");
            info.append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD**\n").append(listNames(discard)).append("\n");
            MessageHelper.replyToMessage(event, info.toString());
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
