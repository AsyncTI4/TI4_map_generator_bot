package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class RemoveAdjacencyOverride extends GameStateSubcommand {

    public RemoveAdjacencyOverride() {
        super(Constants.REMOVE_ADJACENCY_OVERRIDE, "Remove Custom Adjacent Tiles.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tile").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String primaryTile = event.getOption(Constants.PRIMARY_TILE).getAsString().toLowerCase();
        if (primaryTile.isBlank() || !PositionMapper.isTilePositionValid(primaryTile)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again");
            return;
        }

        getGame().removeAdjacentTileOverrides(primaryTile);
    }
}
