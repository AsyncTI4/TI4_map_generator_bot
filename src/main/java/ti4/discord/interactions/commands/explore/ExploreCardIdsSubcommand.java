package ti4.discord.interactions.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

abstract class ExploreCardIdsSubcommand extends GameStateSubcommand {

    ExploreCardIdsSubcommand(String name, String description, String optionDescription) {
        super(name, description, true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, optionDescription).setRequired(true));
    }

    ExploreCardIdsSubcommand(String name, String description) {
        this(name, description, "Exploration card ids. May include multiple comma-separated ids.");
    }

    protected abstract String handleCard(Game game, String id, ExploreModel explore);

    protected static String notFound(String id) {
        return "Card ID " + id + " not found, please retry";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String ids = PatternHelper.SPACE_PATTERN
                .matcher(event.getOption(Constants.EXPLORE_CARD_ID).getAsString())
                .replaceAll("");
        StringBuilder sb = new StringBuilder();
        for (String id : ids.split(",")) {
            sb.append(handleCard(game, id, Mapper.getExplore(id))).append(System.lineSeparator());
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
