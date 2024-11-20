package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

class SwapTwoSystems extends GameStateSubcommand {

    public SwapTwoSystems() {
        super(Constants.SWAP_SYSTEMS, "Swap two systems", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to swap from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to swap to").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tile1ID = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
        Tile tile1 = TileHelper.getTile(event, tile1ID, game);
        if (tile1 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tile1ID + "`. Tile not found");
            return;
        }

        String tile2ID = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME_TO).getAsString().toLowerCase());
        Tile tile2 = TileHelper.getTile(event, tile2ID, game);

        String positionFrom = tile1.getPosition();
        String positionTo = tile2ID; //need to validate position

        if (tile2 != null) { // tile exists, so swap
            positionTo = tile2.getPosition();
            tile2.setPosition(positionFrom);
            game.setTile(tile2);
        } else if (!PositionMapper.isTilePositionValid(positionTo)) { // tile does not exist, so validate the TO position
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid Tile To position: " + positionTo);
            return;
        }

        tile1.setPosition(positionTo);
        game.setTile(tile1);

        game.rebuildTilePositionAutoCompleteList();
    }
}
