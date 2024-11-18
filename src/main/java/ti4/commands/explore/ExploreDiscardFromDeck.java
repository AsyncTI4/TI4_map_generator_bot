package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class ExploreDiscardFromDeck extends ExploreSubcommandData {

    public ExploreDiscardFromDeck() {
        super(Constants.DISCARD_FROM_DECK, "Discard an Exploration Card from the deck.");
        addOptions(idOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            ExploreModel explore = Mapper.getExplore(id);
            if (explore != null) {
                game.discardExplore(id);
                sb.append("Card discarded: ").append(explore.textRepresentation()).append(System.lineSeparator());
            } else {
                sb.append("Card ID ").append(id).append(" not found, please retry").append(System.lineSeparator());
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
