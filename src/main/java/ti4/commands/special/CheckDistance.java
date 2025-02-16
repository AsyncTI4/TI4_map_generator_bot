package ti4.commands.special;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.image.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

class CheckDistance extends GameStateSubcommand {

    public CheckDistance() {
        super(Constants.CHECK_DISTANCE, "Check Distance", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MAX_DISTANCE, "Max distance to check"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String tileID = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        Player player = getPlayer();
        int maxDistance = event.getOption(Constants.MAX_DISTANCE, 8, OptionMapping::getAsInt);
        Map<String, Integer> distances = CheckDistanceHelper.getTileDistances(game, player, tile.getPosition(), maxDistance, true);

        MessageHelper.sendMessageToEventChannel(event, distances.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .sorted()
            .reduce("Distances: \n", (a, b) -> a + "\n" + b));
    }
}
