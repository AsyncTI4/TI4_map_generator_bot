package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.service.explore.ExploreService;

class ExploreFrontier extends GameStateSubcommand {

    public ExploreFrontier() {
        super(Constants.FRONTIER, "Explore a Frontier token on a Tile", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Location of the frontier tile")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.FORCE,
                "True to explore even if there is no frontier token in the system"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileName = event.getOption(Constants.TILE_NAME).getAsString();
        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        Game game = getGame();
        Tile tile = TileHelper.getTile(event, tileName, game);
        ExploreService.expFront(event, tile, game, getPlayer(), force);
    }
}
