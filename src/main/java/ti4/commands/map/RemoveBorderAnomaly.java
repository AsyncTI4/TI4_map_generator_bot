package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class RemoveBorderAnomaly extends MapSubcommandData {
    public RemoveBorderAnomaly() {
        super(Constants.REMOVE_BORDER_ANOMALY, "Remove a border anomaly from a tile");
        addOption(OptionType.STRING, Constants.PRIMARY_TILE, "Tile the border will be linked to", true, true);
        addOption(OptionType.STRING, Constants.PRIMARY_TILE_DIRECTION, "Side of the tile the anomaly will be on", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        String tile = event.getOption(Constants.PRIMARY_TILE, null, OptionMapping::getAsString);
        if (!game.getTileMap().containsKey(tile)) {
            MessageHelper.replyToMessage(event, "Map does not contain that tile");
            return;
        }

        String direction = event.getOption(Constants.PRIMARY_TILE_DIRECTION, null, OptionMapping::getAsString);
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

        game.removeBorderAnomaly(tile, directionVal);
        GameSaveLoadManager.saveGame(game, event);
    }
}
