package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class DiscardExp extends ExploreSubcommandData {

    public DiscardExp() {
        super(Constants.DISCARD, "Discard an Exploration Card from the deck.");
        addOptions(idOption.setRequired(true));
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
                activeMap.discardExplore(id);
                sb.append("Card discarded: ").append(displayExplore(id)).append(System.lineSeparator());
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry").append(System.lineSeparator());
            }
        }
        MessageHelper.replyToMessage(event, sb.toString());
    }
}
