package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyModel;

public class AddBorderAnomaly extends MapSubcommandData {
    public AddBorderAnomaly() {
        super(Constants.ADD_BORDER_ANOMALY, "Add a border anomaly to a tile");
        addOption(OptionType.STRING, Constants.PRIMARY_TILE, "Tile the border will be linked to", true, true);
        addOption(OptionType.STRING, Constants.PRIMARY_TILE_DIRECTION, "Side of the tile the anomaly will be on", true, true);
        addOption(OptionType.STRING, Constants.BORDER_TYPE, "Type of anomaly", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tile = event.getOption(Constants.PRIMARY_TILE, null, OptionMapping::getAsString);
        if (!game.getTileMap().containsKey(tile)) {
            MessageHelper.replyToMessage(event, "Map does not contain that tile");
        }

        String direction = event.getOption(Constants.PRIMARY_TILE_DIRECTION, null, OptionMapping::getAsString);
        String anomalyTypeString = event.getOption(Constants.BORDER_TYPE, null, OptionMapping::getAsString);
        BorderAnomalyModel model = new BorderAnomalyModel();

        BorderAnomalyModel.BorderAnomalyType anomalyType = model.getBorderAnomalyTypeFromString(anomalyTypeString);

        int directionVal = -1;
        switch (direction.toLowerCase()) {
            case "north" -> directionVal = 0;
            case "northeast" -> directionVal = 1;
            case "southeast" -> directionVal = 2;
            case "south" -> directionVal = 3;
            case "southwest" -> directionVal = 4;
            case "northwest" -> directionVal = 5;
        }

        if (directionVal == -1) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid direction");
            return;
        }

        if (game.hasBorderAnomalyOn(tile, directionVal)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile already has an anomaly there!");
            return;
        }

        game.addBorderAnomaly(tile, directionVal, anomalyType);
        GameSaveLoadManager.saveGame(game, event);
    }
}
