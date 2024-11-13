package ti4.commands.fow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class AddCustomAdjacentTile extends FOWSubcommandData {
    public AddCustomAdjacentTile() {
        super(Constants.ADD_CUSTOM_ADJACENT_TILES, "Add Custom Adjacent Tiles. ");
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tile").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADJACENT_TILES, "Adjacent Tiles").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TWO_WAY, "Are added tiles two way connection").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping primaryTileOption = event.getOption(Constants.PRIMARY_TILE);
        if (primaryTileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Primary tile");
            return;
        }

        OptionMapping adjacentTilesOption = event.getOption(Constants.ADJACENT_TILES);
        if (adjacentTilesOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Adjacent tiles");
            return;
        }
        String primaryTile = primaryTileOption.getAsString().toLowerCase();
        String adjacentTiles = adjacentTilesOption.getAsString().toLowerCase();
        if (primaryTile.isBlank() || adjacentTiles.isBlank()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again. Example: primary:0a adjacent:1a,1b,1c,1d");
            return;
        }

        adjacentTiles = adjacentTiles.replace(" ", "");
        String[] tilesSplit = adjacentTiles.split(",");
        List<String> tiles = Arrays.asList(tilesSplit);
        game.addCustomAdjacentTiles(primaryTile, tiles);
        OptionMapping twoWayOption = event.getOption(Constants.TWO_WAY);
        if (twoWayOption != null && twoWayOption.getAsBoolean()) {
            for (String tile : tiles) {
                Map<String, List<String>> customAdjacentTiles = game.getCustomAdjacentTiles();
                List<String> customTiles = customAdjacentTiles.get(tile);
                if (customTiles == null) {
                    customTiles = new ArrayList<>();
                }
                if (!customTiles.contains(primaryTile)) {
                    customTiles.add(primaryTile);
                    game.addCustomAdjacentTiles(tile, customTiles);
                }
            }
        }
    }
}
