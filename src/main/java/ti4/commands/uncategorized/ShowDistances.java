package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.special.CheckDistance;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.generator.MapRenderPipeline;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class ShowDistances extends GameStateCommand {

    public ShowDistances() {
        super(false, true);
    }

    @Override
    public String getName() {
        return Constants.SHOW_DISTANCES;
    }

    @Override
    public String getDescription() {
        return "Shows map with distances to each tile from specified tile";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) &&
            CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        Game game = getGame();
        Player player = getPlayer();
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        int maxDistance = event.getOption(Constants.MAX_DISTANCE, 10, OptionMapping::getAsInt);
        game.setTileDistances(CheckDistance.getTileDistances(game, player, tile.getPosition(), maxDistance, true));

        MapRenderPipeline.render(game, event, DisplayType.map,
                fileUpload -> MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload));
    }
}
