package ti4.commands2.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class RemoveCustomAdjacentTile extends GameStateSubcommand {

    public RemoveCustomAdjacentTile() {
        super(Constants.REMOVE_CUSTOM_ADJACENT_TILES, "Remove Custom Adjacent Tiles.",  true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PRIMARY_TILE, "Primary Tile").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String primaryTile = event.getOption(Constants.PRIMARY_TILE).getAsString().toLowerCase();
        if (primaryTile.isBlank()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Bad data, try again. Example: primary:0a");
            return;
        }

        getGame().removeCustomAdjacentTiles(primaryTile);
    }
}
