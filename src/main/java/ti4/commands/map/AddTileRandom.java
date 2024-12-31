package ti4.commands.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.model.Source.ComponentSource;
import ti4.service.map.AddTileService;
import ti4.service.map.AddTileService.RandomOption;

class AddTileRandom extends GameStateSubcommand {

    public AddTileRandom() {
        super(Constants.ADD_TILE_RANDOM, "Add random tile to map (empty tiles can be duplicates)", true, false);
        addOption(OptionType.STRING, Constants.POSITION, "Tile positions", true, true);
        addOption(OptionType.STRING, Constants.RANDOM_TYPE, "Tile type (blue/red/hs/hl/any)", true, true);
        addOption(OptionType.BOOLEAN, Constants.INCLUDE_ERONOUS_TILES, "Include Eronous tiles");
        addOption(OptionType.BOOLEAN, Constants.DRAW_ONLY, "Only draw, don't add to map");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String positionString = event.getOption(Constants.POSITION).getAsString();
        String randomType = event.getOption(Constants.RANDOM_TYPE).getAsString().trim().toUpperCase();
        boolean drawOnly = event.getOption(Constants.DRAW_ONLY, false, OptionMapping::getAsBoolean);

        Game game = getGame();
        boolean isFowPrivate = game.isFowMode() && event.getChannel().getName().endsWith(Constants.PRIVATE_CHANNEL);
        if (isFowPrivate && !game.isAgeOfExplorationMode()) {
            MessageHelper.replyToMessage(event, "Cannot run this command in a private channel.");
            return;
        }
        
        if (!RandomOption.isValid(randomType)) {
            MessageHelper.replyToMessage(event, "Invalid type: " + randomType);
            return;
        }
        RandomOption randomOption = RandomOption.valueOf(randomType);

        List<String> positions = new ArrayList<>();
        StringTokenizer positionTokenizer = new StringTokenizer(positionString, ",");
        while (positionTokenizer.hasMoreTokens()) {
            String position = positionTokenizer.nextToken().trim();
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Invalid position: " + position);
                return;
            }
            positions.add(position);
        }

        Set<ComponentSource> sources = AddTileService.getSources(event, game);

        List<String> drawnTiles = new ArrayList<>();
        StringBuffer msg = new StringBuffer();
        for (String position : positions) {
            Set<TileModel> existingTileModels = game.getTileMap().values().stream()
                .map(Tile::getTileModel).collect(Collectors.toSet());

            List<TileModel> availableTiles = AddTileService.availableTiles(sources, randomOption, existingTileModels, drawnTiles);
            if (availableTiles.isEmpty()) {
                msg.append("No available tiles found.");
                break;
            }
            Collections.shuffle(availableTiles);
            TileModel randomTile = availableTiles.getFirst();
            drawnTiles.add(randomTile.getId());
            if (!drawOnly) {
                AddTileService.addTile(getGame(), new Tile(randomTile.getId(), position));
            }

            msg.append((drawOnly ? "Drew " : "Added ") + randomTile.getEmbedTitle() 
                + " to " + position + " from " + availableTiles.size() + " options\n");
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), msg.toString());

        if (!drawOnly) game.rebuildTilePositionAutoCompleteList();
    }
}
