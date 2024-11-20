package ti4.commands2.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.map.TeaspoonMapService;

class InitTspmap extends GameStateSubcommand {

    public InitTspmap() {
        super(Constants.INIT_TSPMAP, "Initialize the map to have the hyperlanes and edge adjacencies of Tispoon's endless map layout", true, false);
        addOption(OptionType.STRING, Constants.CONFIRM, "Type 'YES' to confirm. This command can erase tiles", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!"YES".equals(event.getOption(Constants.CONFIRM).getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        game.clearAdjacentTileOverrides();
        TeaspoonMapService.addTspmapHyperlanes(game);
        TeaspoonMapService.addTspmapEdgeAdjacencies(game);
    }
}
