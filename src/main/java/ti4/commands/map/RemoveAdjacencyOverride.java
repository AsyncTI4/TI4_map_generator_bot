package ti4.commands.map;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class RemoveAdjacencyOverride extends GameStateSubcommand {

    public RemoveAdjacencyOverride() {
        super(Constants.REMOVE_ADJACENCY_OVERRIDE, "Remove Custom Adjacencies", true, true);
        addOptions(new OptionData(
                        OptionType.STRING, Constants.PRIMARY_TILE, "Tiles to remove adjacencies or ALL to remove all")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String primaryTile = event.getOption(Constants.PRIMARY_TILE).getAsString();
        if (primaryTile.isBlank()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again");
            return;
        }

        if (Constants.ALL.equals(primaryTile)) {
            getGame().clearAdjacentTileOverrides();
        } else {
            List<String> primaryTiles = Helper.getListFromCSV(primaryTile);
            for (String tile : primaryTiles) {
                if (!PositionMapper.isTilePositionValid(tile)) {
                    MessageHelper.replyToMessage(event, "Tile position '" + tile + "' is invalid");
                    continue;
                }

                getGame().removeAdjacentTileOverrides(tile);
            }
        }
    }
}
