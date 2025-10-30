package ti4.commands.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class AddCustomAdjacentTile extends GameStateSubcommand {

    public AddCustomAdjacentTile() {
        super(Constants.ADD_CUSTOM_ADJACENT_TILES, "Add Custom Adjacent Tiles.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tile").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADJACENT_TILES, "Adjacent Tiles").setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TWO_WAY, "Are added tiles two way connection"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String primaryTile =
                event.getOption(Constants.PRIMARY_TILE).getAsString().toLowerCase();
        String adjacentTiles =
                event.getOption(Constants.ADJACENT_TILES).getAsString().toLowerCase();
        if (primaryTile.isBlank() || adjacentTiles.isBlank()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Bad data, try again. Example: primary:0a adjacent:1a,1b,1c,1d");
            return;
        }

        List<String> tiles = Helper.getListFromCSV(adjacentTiles);
        Game game = getGame();

        List<String> customTiles =
                new ArrayList<>(game.getCustomAdjacentTiles().getOrDefault(primaryTile, Collections.emptyList()));
        for (String tile : tiles) {
            if (!customTiles.contains(tile)) {
                customTiles.add(tile);
            }
        }
        game.addCustomAdjacentTiles(primaryTile, customTiles);
        OptionMapping twoWayOption = event.getOption(Constants.TWO_WAY);
        if (twoWayOption != null && twoWayOption.getAsBoolean()) {
            for (String tile : tiles) {
                List<String> customReverseTiles =
                        new ArrayList<>(game.getCustomAdjacentTiles().getOrDefault(tile, Collections.emptyList()));
                if (!customReverseTiles.contains(primaryTile)) {
                    customReverseTiles.add(primaryTile);
                    game.addCustomAdjacentTiles(tile, customReverseTiles);
                }
            }
        }
    }
}
