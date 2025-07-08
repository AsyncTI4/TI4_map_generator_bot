package ti4.commands.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

class RemoveBorderAnomaly extends GameStateSubcommand {

    public RemoveBorderAnomaly() {
        super(Constants.REMOVE_BORDER_ANOMALY, "Remove border anomalies", true, false);
        addOption(OptionType.STRING, Constants.PRIMARY_TILE, "Tile the border is linked to or ALL to remove all border anomalies from the map", true, true);
        addOption(OptionType.STRING, Constants.PRIMARY_TILE_DIRECTION, "Side of the system the anomaly is on", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String tilesString = event.getOption(Constants.PRIMARY_TILE).getAsString();
        Set<String> tiles = new HashSet<>();
        if (Constants.ALL.equals(tilesString)) {
            tiles = game.getTileMap().values().stream().map(Tile::getTileID).collect(Collectors.toSet());
        } else {
            tiles = AddBorderAnomaly.resolveTiles(event, game);
        }

        Set<Integer> directions = AddBorderAnomaly.resolveDirections(event);

        if (Constants.ALL.equals(tilesString) && directions.isEmpty()) {
            //No need to loop, just set as empty
            int amountOfBorderAnomalies = game.getBorderAnomalies().size();
            game.setBorderAnomalies(new ArrayList<>());
            MessageHelper.replyToMessage(event, "All " + amountOfBorderAnomalies + " border anomalies removed.");
            return;
        }

        if (directions.isEmpty()) {
            directions = Set.of(0, 1, 2, 3, 4, 5);
        }

        int amountRemoved = 0;
        for (String tile : tiles) {
            for (int d : directions) {
                if (game.hasBorderAnomalyOn(tilesString, d)) {
                    game.removeBorderAnomaly(tile, d);
                    amountRemoved++;
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Anomalies removed: " + amountRemoved);
    }
}
