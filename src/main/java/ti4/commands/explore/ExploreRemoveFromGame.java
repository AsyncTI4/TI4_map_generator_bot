package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

class ExploreRemoveFromGame extends GameStateSubcommand {

    public ExploreRemoveFromGame() {
        super(Constants.REMOVE, "Remove an Exploration card from the game.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card ids. May include multiple comma-separated ids.").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            ExploreModel explore = Mapper.getExplore(id);
            game.purgeExplore(id);
            if (explore != null) {
                sb.append("Exploration card removed: ").append(explore.textRepresentation()).append(System.lineSeparator());
            } else {
                sb.append("Removed id without matching card: ").append(id).append(System.lineSeparator());
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
