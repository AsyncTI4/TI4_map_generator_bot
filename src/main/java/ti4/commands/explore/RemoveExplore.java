package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class RemoveExplore extends ExploreSubcommandData {

    public RemoveExplore() {
        super(Constants.REMOVE, "Remove an Exploration card from the game.");
        addOptions(idOption.setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
        String[] idList = ids.split(",");
        StringBuilder sb = new StringBuilder();
        for (String id : idList) {
            if (Mapper.getExplore(id) != null) {
                game.purgeExplore(id);
                sb.append("Exploration card removed: ").append(displayExplore(id)).append(System.lineSeparator());
            } else {
                game.purgeExplore(id);
                sb.append("Removed id without matching card: ").append(id).append(System.lineSeparator());
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}