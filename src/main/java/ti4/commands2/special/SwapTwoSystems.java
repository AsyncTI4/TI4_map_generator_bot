package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

class SwapTwoSystems extends GameStateSubcommand {

    public SwapTwoSystems() {
        super(Constants.SWAP_SYSTEMS, "Swap two systems", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to swap from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to swap to").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tileFrom = CommandHelper.getTile(event, game);
        if (tileFrom == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find the tile you're moving from.");
            return;
        }

        Tile tileTo = CommandHelper.getTile(event, game, event.getOption(Constants.TILE_NAME_TO).getAsString());
        if (tileTo == null) {
            BotLogger.log("Could not find the tile you're moving to.");
            return;
        }

        String positionFrom = tileFrom.getPosition();

        // tile exists, so swap
        String positionTo = tileTo.getPosition();
        tileTo.setPosition(positionFrom);
        game.setTile(tileTo);

        tileFrom.setPosition(positionTo);
        game.setTile(tileFrom);

        game.rebuildTilePositionAutoCompleteList();
    }
}
