package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

class ExploreDrawAndDiscard extends GameStateSubcommand {

    public ExploreDrawAndDiscard() {
        super(Constants.DRAW_AND_DISCARD, "Draw from a specified Exploration Deck.", true, true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of cards to draw (default 1)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);

        StringBuilder sb = new StringBuilder();
        Game game = getGame();
        for (int i = 0; i < count; i++) {
            String cardID = game.drawExplore(
                    event.getOption(Constants.TRAIT).getAsString().toLowerCase());
            ExploreModel explore = Mapper.getExplore(cardID);
            sb.append(explore.textRepresentation()).append(System.lineSeparator());
        }
        sb.append("Cards have been discarded. Resolve effects and/or purge manually.\n");
        sb.append("To choose a card to keep/use, run this command: `/explore use explore_card_id:{ID}`");
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
