package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class RemoveCustomAdjacentTile extends FOWSubcommandData {
    public RemoveCustomAdjacentTile() {
        super(Constants.REMOVE_CUSTOM_ADJACENT_TILES, "Remove Custom Adjacent Tiles. ");
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tile").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping primaryTileOption = event.getOption(Constants.PRIMARY_TILE);
        if (primaryTileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Primary tile");
            return;
        }
        String primaryTile = primaryTileOption.getAsString().toLowerCase();
        if (primaryTile.isBlank()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again. Example: primary:0a");
            return;
        }

        game.removeCustomAdjacentTiles(primaryTile);
    }
}
