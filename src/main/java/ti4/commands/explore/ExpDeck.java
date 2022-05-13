package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

public class ExpDeck extends ExploreSubcommandData {
    public ExpDeck() {
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
        for (int i = 0; i < count; i++) {
            String userID = event.getUser().getId();
            Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
            String cardID = activeMap.drawExplore(event.getOption(Constants.EXPLORE_TYPE).getAsString().toLowerCase());
            MessageHelper.replyToMessage(event, displayExplore(cardID));
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Cards have been discarded. Resolve effects and/or purge manually.");
    }
}