package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class AddAdjacencyOverride extends GameStateSubcommand {

    public AddAdjacencyOverride() {
        super(Constants.ADD_ADJACENCY_OVERRIDE, "Add Custom Adjacent Tiles.", true, true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary tile position").setRequired(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.PRIMARY_TILE_DIRECTION,
                        "Direction the second tile is from the primary tile for linking hyperlanes")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SECONDARY_TILE, "Secondary tile position")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String primaryTile =
                event.getOption(Constants.PRIMARY_TILE).getAsString().toLowerCase();
        int direction;
        switch (event.getOption(Constants.PRIMARY_TILE_DIRECTION).getAsString().toLowerCase()) {
            case "n", "north" -> direction = 0;
            case "ne", "northeast" -> direction = 1;
            case "se", "southeast" -> direction = 2;
            case "s", "south" -> direction = 3;
            case "sw", "southwest" -> direction = 4;
            case "nw", "northwest" -> direction = 5;
            default -> direction = -1;
        }

        String secondaryTile =
                event.getOption(Constants.SECONDARY_TILE).getAsString().toLowerCase();
        if (primaryTile.isBlank() || secondaryTile.isBlank() || direction == -1) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again");
            return;
        }
        getGame().addAdjacentTileOverride(primaryTile, direction, secondaryTile);
    }
}
