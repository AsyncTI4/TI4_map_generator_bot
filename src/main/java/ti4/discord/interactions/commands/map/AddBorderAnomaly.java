package ti4.discord.interactions.commands.map;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
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
        BorderAnomalyModel anomalyType = Mapper.resolveBorderAnomaly(anomalyTypeString);
        if (anomalyType == null) {
            sendUnknownTypeMessage(event, anomalyTypeString);
            return;
        }

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
                    game.addBorderAnomaly(tile, d, anomalyType.getId());
                    amountAdded++;
                }
            }
        }
        sb.append(anomalyType.getName()).append(" anomalies added: ").append(amountAdded);
        if (amountAdded > 0 && anomalyType.getAutomation().needsPlayerAttention()) {
            sb.append("\n-# ")
                    .append(anomalyType.getName())
                    .append(" borders are ")
                    .append(anomalyType.getAutomation().getLabel())
                    .append(": ")
                    .append(anomalyType.getAutomationNotes());
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    static void sendUnknownTypeMessage(SlashCommandInteractionEvent event, String anomalyTypeString) {
        String validTypes = Mapper.getBorderAnomalies().stream()
                .map(BorderAnomalyModel::getName)
                .collect(Collectors.joining(", "));
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Unknown border anomaly type `" + anomalyTypeString + "`. Valid types: " + validTypes + ".");
    }

    public static Set<Integer> resolveDirections(SlashCommandInteractionEvent event) {
        Set<String> directionsString =
                Helper.getSetFromCSV(event.getOption(Constants.PRIMARY_TILE_DIRECTION, "", OptionMapping::getAsString));
        Set<Integer> directions = new HashSet<>();
        for (String dir : directionsString) {
            Integer direction = parseDirection(dir);
            if (direction == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid direction " + dir);
            } else {
                directions.add(direction);
            }
        }
        return directions;
    }

    public static Integer parseDirection(String dir) {
        return switch (dir) {
            case "north", "n" -> 0;
            case "northeast", "ne" -> 1;
            case "southeast", "se" -> 2;
            case "south", "s" -> 3;
            case "southwest", "sw" -> 4;
            case "northwest", "nw" -> 5;
            case null, default -> null;
        };
    }
}
