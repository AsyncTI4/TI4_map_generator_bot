package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Arrays;
import java.util.List;

public class AddCustomAdjacentTile extends FOWSubcommandData {
    public AddCustomAdjacentTile() {
        super(Constants.ADD_CUSTOM_ADJACENT_TILES, "Add Custom Adjacent Tiles. ");
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tile").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADJACENT_TILES, "Adjacent Tiles").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping primaryTileOption = event.getOption(Constants.PRIMARY_TILE);
        if (primaryTileOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Primary tile");
            return;
        }

        OptionMapping adjacentTilesOption = event.getOption(Constants.ADJACENT_TILES);
        if (adjacentTilesOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Adjacent tiles");
            return;
        }
        String primaryTile = primaryTileOption.getAsString().toLowerCase();
        String adjacentTiles = adjacentTilesOption.getAsString().toLowerCase();
        if (primaryTile.isBlank() || adjacentTiles.isBlank()){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again. Example: primary:0a adjacent:1a,1b,1c,1d");
            return;
        }

        adjacentTiles = adjacentTiles.replace(" ", "");
        List<String> tiles = Arrays.asList(adjacentTiles.split(","));
        activeMap.addCustomAdjacentTiles(primaryTile, tiles);
    }
}
