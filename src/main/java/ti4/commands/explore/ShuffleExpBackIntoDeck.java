package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class ShuffleExpBackIntoDeck extends ExploreSubcommandData {

    public ShuffleExpBackIntoDeck() {
        super(Constants.SHUFFLE_BACK_INTO_DECK, "Shuffle an Exploration card back into the deck, including purged cards");
        addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card ID sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            String card = Mapper.getExplore(id);
            if (card != null) {
                activeMap.addExplore(id);
                sb.append("Card shuffled into exploration deck: ").append(displayExplore(id)).append(System.lineSeparator());
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry").append(System.lineSeparator());
            }
        }
        sendMessage(sb.toString());
    }
}
