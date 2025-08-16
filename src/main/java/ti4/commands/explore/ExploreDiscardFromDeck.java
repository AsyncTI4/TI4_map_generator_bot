package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

class ExploreDiscardFromDeck extends GameStateSubcommand {

    ExploreDiscardFromDeck() {
        super(Constants.DISCARD_FROM_DECK, "Discard an Exploration Card from the deck.", true, true);
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.EXPLORE_CARD_ID,
                        "Exploration card ids. May include multiple comma-separated ids.")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String ids = PatternHelper.SPACE_PATTERN
                .matcher(event.getOption(Constants.EXPLORE_CARD_ID).getAsString())
                .replaceAll("");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            ExploreModel explore = Mapper.getExplore(id);
            if (explore != null) {
                game.discardExplore(id);
                sb.append("Card discarded: ")
                        .append(explore.textRepresentation())
                        .append(System.lineSeparator());
            } else {
                sb.append("Card ID ")
                        .append(id)
                        .append(" not found, please retry")
                        .append(System.lineSeparator());
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
