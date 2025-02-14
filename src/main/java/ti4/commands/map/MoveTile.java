package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class MoveTile extends GameStateSubcommand {

    public MoveTile() {
        super(Constants.MOVE_TILE, "Move a tile to another location", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to move")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Position to move to (must have no tile)")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile movingTile = CommandHelper.getTile(event, game);
        if (movingTile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find the tile you're trying to move.");
            return;
        }

        String tileToPosition = event.getOption(Constants.POSITION).getAsString();

        if (game.getTileMap().containsKey(tileToPosition)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Oops, a tile already exists here: " + tileToPosition);
            return;
        }
        if (!PositionMapper.isTilePositionValid(tileToPosition)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid position: " + tileToPosition);
            return;
        }

        MessageHelper.sendMessageToEventChannel(
                event,
                "Moved tile " + movingTile.getRepresentation() + " from " + movingTile.getPosition() + " to "
                        + tileToPosition);

        movingTile.setPosition(tileToPosition);
        game.setTile(movingTile);

        game.rebuildTilePositionAutoCompleteList();
    }
}
