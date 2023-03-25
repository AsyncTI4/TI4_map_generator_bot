package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class RemoveAdjacencyOverride extends FOWSubcommandData {
    public RemoveAdjacencyOverride() {
        super(Constants.REMOVE_ADJACENCY_OVERRIDE, "Remove Custom Adjacent Tiles. ");
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tile").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping primaryTileOption = event.getOption(Constants.PRIMARY_TILE);
        if (primaryTileOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Primary tile");
            return;
        }
        String primaryTile = primaryTileOption.getAsString().toLowerCase();
        if (primaryTile.isBlank() || !PositionMapper.isTilePositionValid(primaryTile, activeMap)){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again");
            return;
        }

        activeMap.removeAdjacentTileOverrides(primaryTile);
    }
}
