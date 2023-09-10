package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;

public class ExploreAndDiscard extends ExploreSubcommandData {
    public ExploreAndDiscard() {
        super(Constants.DRAW_AND_DISCARD, "Draw from a specified Exploration Deck.");
        addOptions(
                typeOption.setRequired(true),
                new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of cards to draw (default 1)")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping countOpt = event.getOption(Constants.COUNT);
        int count;
        if (countOpt != null) {
            count = countOpt.getAsInt();
        } else {
            count = 1;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String userID = event.getUser().getId();
            Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
            String cardID = activeGame.drawExplore(event.getOption(Constants.TRAIT).getAsString().toLowerCase());
            sb.append(displayExplore(cardID)).append(System.lineSeparator());
        }
        sb.append("Cards have been discarded. Resolve effects and/or purge manually.\n");
        sb.append("To choose a card to keep/use, run this command: `/explore use explore_card_id:{ID}`");
        sendMessage(sb.toString());
    }
}