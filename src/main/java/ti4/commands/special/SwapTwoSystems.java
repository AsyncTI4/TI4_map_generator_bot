package ti4.commands.special;

import java.io.File;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.GenerateMap;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.*;
import ti4.message.MessageHelper;

public class SwapTwoSystems extends SpecialSubcommandData {
    public SwapTwoSystems() {
        super(Constants.SWAP_SYSTEMS, "Swap two systems");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to swap from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to swap to").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }

        OptionMapping tileOptionTo = event.getOption(Constants.TILE_NAME_TO);
        if (tileOptionTo == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeGame);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        String tileIDTo = AliasHandler.resolveTile(tileOptionTo.getAsString().toLowerCase());
        Tile tileTo = AddRemoveUnits.getTile(event, tileIDTo, activeGame);
        if (tileTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileIDTo:  `" + tileID + "`. Tile not found");
            return;
        }

        String position = tile.getPosition();
        String positionTo = tileTo.getPosition();
        tile.setPosition(positionTo);
        tileTo.setPosition(position);
        activeGame.setTile(tile);
        activeGame.setTile(tileTo);
        activeGame.rebuildTilePositionAutoCompleteList();
        DisplayType displayType = DisplayType.map;
        File file = GenerateMap.getInstance().saveImage(activeGame, displayType, event);
        MessageHelper.sendFileToChannel(event.getChannel(), file);
    }
}
