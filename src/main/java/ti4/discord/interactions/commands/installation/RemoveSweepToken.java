package ti4.discord.interactions.commands.installation;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;

class RemoveSweepToken extends GameStateSubcommand {

    public RemoveSweepToken() {
        super(Constants.REMOVE_SWEEP_TOKEN, "Remove a sweep token from the selected system", true, true);
        addOptions(CommonOptions.tileName("System to remove a sweep token from"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        String tileName = event.getOption(Constants.TILE_NAME).getAsString().toLowerCase();
        tileName = StringUtils.substringBefore(tileName, " ");
        Tile tile = TileHelper.getTile(event, tileName, getGame());
        if (tile == null) return;

        String sweepToken = Mapper.getSweepID(getPlayer().getColor());
        tile.removeCC(sweepToken);
    }
}
