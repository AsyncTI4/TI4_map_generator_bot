package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.special.CheckDistance;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.MapGenerator;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class ShowDistances implements Command {

    @Override
    public String getActionID() {
        return Constants.SHOW_DISTANCES;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }

        Game userActiveGame = GameManager.getInstance().getUserActiveGame(event.getUser().getId());
        if (userActiveGame == null) {
            MessageHelper.replyToMessage(event, "No active game set, need to specify what map to show");
            return false;
        }

        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game;
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        GameManager gameManager = GameManager.getInstance();
        if (option != null) {
            String mapName = option.getAsString().toLowerCase();
            game = gameManager.getGame(mapName);
        } else {
            game = gameManager.getUserActiveGame(event.getUser().getId());
        }

        Player player = game.getPlayer(event.getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        int maxDistance = event.getOption(Constants.MAX_DISTANCE, 10, OptionMapping::getAsInt);
        game.setTileDistances(CheckDistance.getTileDistances(game, player, tile.getPosition(), maxDistance, true));

        MapGenerator.saveImage(game, DisplayType.map, event, true)
            .thenAccept(fileUpload -> MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), "Shows map with distances to each tile from specified tile")
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.INTEGER, Constants.MAX_DISTANCE, "Max distance to check")));
    }
}
