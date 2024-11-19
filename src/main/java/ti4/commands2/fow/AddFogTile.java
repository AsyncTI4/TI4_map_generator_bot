package ti4.commands2.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ResourceHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class AddFogTile extends GameStateSubcommand {

    public AddFogTile() {
        super(Constants.ADD_FOG_TILE, "Add a Fog of War tile to the map.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LABEL, "How you want the tile to be labeled").setMaxLength(10));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String position = event.getOption(Constants.POSITION).getAsString().toLowerCase();
        if (!ti4.image.PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Tile position is not allowed");
            return;
        }

        String planetTileName = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
        String tileName = Mapper.getTileID(planetTileName);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return;
        }

        OptionMapping labelMapping = event.getOption(Constants.LABEL);
        String label = labelMapping == null ? "" : labelMapping.getAsString();
        getPlayer().addFogTile(planetTileName, position, label);
    }
}
