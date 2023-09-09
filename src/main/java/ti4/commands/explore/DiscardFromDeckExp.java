package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;

public class DiscardFromDeckExp extends ExploreSubcommandData {

    public DiscardFromDeckExp() {
        super(Constants.DISCARD_FROM_DECK, "Discard an Exploration Card from the deck.");
        addOptions(idOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            String card = Mapper.getExplore(id);
            if (card != null) {
                activeGame.discardExplore(id);
                sb.append("Card discarded: ").append(displayExplore(id)).append(System.lineSeparator());
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry").append(System.lineSeparator());
            }
        }
        sendMessage(sb.toString());
    }
}
