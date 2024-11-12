package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class AddAdjacencyOverride extends GameStateSubcommand {

    public AddAdjacencyOverride() {
        super(Constants.ADD_ADJACENCY_OVERRIDE, "Add Custom Adjacent Tiles.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary tile position").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE_DIRECTION, "Direction the second tile is from the primary tile for linking hyperlanes")
            .setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SECONDARY_TILE, "Secondary tile position").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping primaryTileOption = event.getOption(Constants.PRIMARY_TILE);
        if (primaryTileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify primary tile");
            return;
        }

        OptionMapping directionOption = event.getOption(Constants.PRIMARY_TILE_DIRECTION);
        if (directionOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify direction");
            return;
        }

        OptionMapping secondaryTileOption = event.getOption(Constants.SECONDARY_TILE);
        if (secondaryTileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify secondary tile");
            return;
        }

        String primaryTile = primaryTileOption.getAsString().toLowerCase();
        int direction;
        switch (directionOption.getAsString().toLowerCase()) {
            case "north" -> direction = 0;
            case "northeast" -> direction = 1;
            case "southeast" -> direction = 2;
            case "south" -> direction = 3;
            case "southwest" -> direction = 4;
            case "northwest" -> direction = 5;
            default -> direction = -1;
        }

        String secondaryTile = secondaryTileOption.getAsString().toLowerCase();

        if (primaryTile.isBlank() || secondaryTile.isBlank() || direction == -1) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again");
            return;
        }
        getGame().addAdjacentTileOverride(primaryTile, direction, secondaryTile);
    }
}
