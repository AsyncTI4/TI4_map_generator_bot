package ti4.commands.map;

import java.util.ArrayList;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyModel;

class RemoveBorderAnomaly extends GameStateSubcommand {

    public RemoveBorderAnomaly() {
        super(Constants.REMOVE_BORDER_ANOMALY, "Remove border anomalies", true, false);
        addOption(
                OptionType.STRING,
                Constants.PRIMARY_TILE,
                "Tile the border is linked to or ALL to remove all border anomalies from the map",
                true,
                true);
        addOption(
                OptionType.STRING,
                Constants.PRIMARY_TILE_DIRECTION,
                "Side of the system the anomaly is on",
                false,
                true);
        addOption(OptionType.STRING, Constants.BORDER_TYPE, "Type of anomaly to remove", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tilesString = event.getOption(Constants.PRIMARY_TILE).getAsString();
        BorderAnomalyModel.BorderAnomalyType anomalyType = new BorderAnomalyModel()
                .getBorderAnomalyTypeFromString(
                        event.getOption(Constants.BORDER_TYPE, null, OptionMapping::getAsString));
        Set<String> tiles = Helper.getSetFromCSV(tilesString);

        if (Constants.ALL.equals(tilesString)) {
            tiles = game.getTileMap().keySet();
        }
        Set<Integer> directions = AddBorderAnomaly.resolveDirections(event);

        if (Constants.ALL.equals(tilesString) && directions.isEmpty() && anomalyType == null) {
            // No need to loop, just set as empty
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
                if (game.getBorderAnomalies().stream()
                        .anyMatch(anom -> anom.getTile().equals(tile)
                                && anom.getDirection() == d
                                && (anomalyType == null || anom.getType() == anomalyType))) {
                    game.removeBorderAnomaly(tile, d);
                    amountRemoved++;
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Anomalies removed: " + amountRemoved);
    }
}
