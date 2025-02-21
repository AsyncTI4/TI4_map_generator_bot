package ti4.commands.special;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.fow.RiftSetModeService;

class SwapTwoSystems extends GameStateSubcommand {

    public SwapTwoSystems() {
        super(Constants.SWAP_SYSTEMS, "Swap two systems", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to swap from or RND").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to swap to or RND").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String from = event.getOption(Constants.TILE_NAME).getAsString();
        String to = event.getOption(Constants.TILE_NAME_TO).getAsString();

        Tile tileFrom = "RND".equalsIgnoreCase(from) ? getRandomTile() : CommandHelper.getTile(event, game);
        if (tileFrom == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find tile: " + from);
            return;
        }

        Tile tileTo = "RND".equalsIgnoreCase(to) ? getRandomTile() : CommandHelper.getTile(event, game, to);
        if (tileTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find tile: " + to);
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
        FowCommunicationThreadService.checkAllCommThreads(game);
        MessageHelper.replyToMessage(event, "Swapped " + tileTo.getPosition() + " and " + tileFrom.getPosition());
        RiftSetModeService.swappedSystems(game);
    }

    private Tile getRandomTile() {
        Set<String> EXCLUDED_POSITIONS = Set.of("tl", "tr", "bl", "br");
        List<Tile> availableTiles = getGame().getTileMap().values().stream()
                .filter(tile -> !EXCLUDED_POSITIONS.contains(tile.getPosition()))
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .collect(Collectors.toList());

        if (availableTiles.isEmpty()) {
            return null; 
        }

        return availableTiles.get(new Random().nextInt(availableTiles.size()));
    }
}
