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

class RemoveCustomAdjacentTile extends GameStateSubcommand {

    public RemoveCustomAdjacentTile() {
        super(Constants.REMOVE_CUSTOM_ADJACENT_TILES, "Remove Custom Adjacent Tiles.",  true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tiles or ALL to remove all").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String primaryTile = event.getOption(Constants.PRIMARY_TILE).getAsString();
        if (primaryTile.isBlank()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again.");
            return;
        }

        if (Constants.ALL.equals(primaryTile)) {
            getGame().clearCustomAdjacentTiles();
        } else {
            List<String> primaryTiles = Helper.getListFromCSV(primaryTile);
            for (String tile : primaryTiles) {
                if (!PositionMapper.isTilePositionValid(tile)) {
                    MessageHelper.replyToMessage(event, "Tile position '" + tile + "' is invalid");
                    continue;
                }

                getGame().removeCustomAdjacentTiles(tile);
            }
        }
    }
}
