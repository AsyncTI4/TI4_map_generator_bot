package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

public class SwapTwoSystems extends SpecialSubcommandData {
    public SwapTwoSystems() {
        super(Constants.SWAP_SYSTEMS, "Swap two systems with there places");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to swap from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to swap to").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
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
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile not found");
            return;
        }

        String tileIDTo = AliasHandler.resolveTile(tileOptionTo.getAsString().toLowerCase());
        Tile tileTo = AddRemoveUnits.getTile(event, tileIDTo, activeMap);
        if (tileTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile not found");
            return;
        }

        String position = tile.getPosition();
        String positionTo = tileTo.getPosition();
        tile.setPosition(positionTo);
        tileTo.setPosition(position);
        activeMap.setTile(tile);
        activeMap.setTile(tileTo);
        activeMap.rebuildTilePositionAutoCompleteList();
    }
}
