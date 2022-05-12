package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

import java.util.StringTokenizer;

public class DiscardExp extends ExploreSubcommandData {

    public DiscardExp() {
        super(Constants.DISCARD, "Discard an Exploration Card from the deck.");
        addOptions(idOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        StringTokenizer idTokenizer = new StringTokenizer(ids, ",");
        int count = idTokenizer.countTokens();
        for (int i = 0; i < count; i++) {
            String id = idTokenizer.nextToken();
            StringBuilder sb = new StringBuilder();
            String card = Mapper.getExplore(id);
            if (card != null) {
                activeMap.discardExplore(id);
                sb.append("Card discarded: \n").append(displayExplore(id));
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry");
            }
            MessageHelper.replyToMessage(event, sb.toString());
        }
    }
}
