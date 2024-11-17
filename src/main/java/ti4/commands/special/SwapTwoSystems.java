package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class SwapTwoSystems extends SpecialSubcommandData {
    public SwapTwoSystems() {
        super(Constants.SWAP_SYSTEMS, "Swap two systems");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to swap from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to swap to").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }

        OptionMapping tileOptionTo = event.getOption(Constants.TILE_NAME_TO);
        if (tileOptionTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tile1ID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile1 = TileHelper.getTile(event, tile1ID, game);
        if (tile1 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tile1ID + "`. Tile not found");
            return;
        }

        String tile2ID = AliasHandler.resolveTile(tileOptionTo.getAsString().toLowerCase());
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
        } else {
            //game.removeTile(positionFrom);
        }

        tile1.setPosition(positionTo);
        game.setTile(tile1);

        game.rebuildTilePositionAutoCompleteList();
        ShowGame.simpleShowGame(game, event, DisplayType.map);
    }
}
