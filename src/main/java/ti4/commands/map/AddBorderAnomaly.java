package ti4.commands.map;

import java.util.HashSet;
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

public class AddBorderAnomaly extends GameStateSubcommand {

    public AddBorderAnomaly() {
        super(Constants.ADD_BORDER_ANOMALY, "Add a border anomaly to a tile", true, false);
        addOption(OptionType.STRING, Constants.PRIMARY_TILE, "Tile the border will be linked to", true, true);
        addOption(
                OptionType.STRING,
                Constants.PRIMARY_TILE_DIRECTION,
                "Side of the system the anomaly will be on",
                true,
                true);
        addOption(OptionType.STRING, Constants.BORDER_TYPE, "Type of anomaly", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Set<String> tiles =
                Helper.getSetFromCSV(event.getOption(Constants.PRIMARY_TILE).getAsString());
        Set<Integer> directions = resolveDirections(event);

        String anomalyTypeString = event.getOption(Constants.BORDER_TYPE).getAsString();
        BorderAnomalyModel.BorderAnomalyType anomalyType =
                new BorderAnomalyModel().getBorderAnomalyTypeFromString(anomalyTypeString);

        StringBuilder sb = new StringBuilder();
        int amountAdded = 0;
        for (String tile : tiles) {
            for (int d : directions) {
                if (game.hasBorderAnomalyOn(tile, d)) {
                    sb.append("Tile ")
                            .append(tile)
                            .append(" already has an anomaly in position ")
                            .append(d)
                            .append(".\n");
                } else {
                    game.addBorderAnomaly(tile, d, anomalyType);
                    amountAdded++;
                }
            }
        }
        sb.append(anomalyType.getName()).append(" anomalies added: ").append(amountAdded);
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    public static Set<Integer> resolveDirections(SlashCommandInteractionEvent event) {
        Set<String> directionsString =
                Helper.getSetFromCSV(event.getOption(Constants.PRIMARY_TILE_DIRECTION, "", OptionMapping::getAsString));
        Set<Integer> directions = new HashSet<>();
        for (String dir : directionsString) {
            switch (dir) {
                case "north", "n" -> directions.add(0);
                case "northeast", "ne" -> directions.add(1);
                case "southeast", "se" -> directions.add(2);
                case "south", "s" -> directions.add(3);
                case "southwest", "sw" -> directions.add(4);
                case "northwest", "nw" -> directions.add(5);
                default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid direction " + dir);
            }
        }
        return directions;
    }
}
