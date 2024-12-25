package ti4.commands2.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.model.Source.ComponentSource;
import ti4.service.map.AddTileService;

class AddTileRandom extends GameStateSubcommand {

    private final List<String> TYPES = Arrays.asList("blue", "red", "hs", "green", "hyperlane");

    public AddTileRandom() {
        super(Constants.ADD_TILE_RANDOM, "Add random tile to map (empty tiles can be duplicates)", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile positions", true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_TYPE, "Tile type (blue/red/hs/hyperlane)", true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ERONOUS_TILES, "Include Eronous tiles"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DRAW_ONLY, "Only draw the tile, don't add to map"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String positionString = event.getOption(Constants.POSITION).getAsString();
        String type = event.getOption(Constants.TILE_TYPE).getAsString().trim().toLowerCase();
        boolean eronousTiles = event.getOption(Constants.INCLUDE_ERONOUS_TILES, false, OptionMapping::getAsBoolean);
        boolean drawOnly = event.getOption(Constants.DRAW_ONLY, false, OptionMapping::getAsBoolean);

        Game game = getGame();
        boolean isFowPrivate = game.isFowMode() && event.getChannel().getName().endsWith(Constants.PRIVATE_CHANNEL);
        if (isFowPrivate && !game.isAgeOfExplorationMode()) {
            MessageHelper.replyToMessage(event, "Cannot run this command in a private channel.");
            return;
        }
        
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

        if (!TYPES.contains(type)) {
            MessageHelper.replyToMessage(event, "Invalid type: " + type);
            return;
        }

        Set<ComponentSource> sources = new HashSet<>();
        sources.add(ComponentSource.base);
        sources.add(ComponentSource.codex1);
        sources.add(ComponentSource.codex2);
        sources.add(ComponentSource.codex3);
        sources.add(ComponentSource.pok);
        if (game.isDiscordantStarsMode()) {
            sources.add(ComponentSource.ds);
            sources.add(ComponentSource.uncharted_space);
        }
        if (eronousTiles) {
            sources.add(ComponentSource.eronous);
        }

        List<TileModel> drawnTiles = new ArrayList<>();
        StringBuffer msg = new StringBuffer();
        for (String position : positions) {
            List<TileModel> availableTiles = availableTiles(game, sources, type, drawnTiles);
            if (availableTiles.isEmpty()) {
                msg.append("No available tiles found.");
                break;
            }
            Collections.shuffle(availableTiles);
            TileModel randomTile = availableTiles.getFirst();
            drawnTiles.add(randomTile);
            if (!drawOnly) {
                AddTileService.addTile(getGame(), new Tile(randomTile.getId(), position));
            }

            msg.append((drawOnly ? "Drew " : "Added ") + randomTile.getEmbedTitle() 
                + " to " + position + " from " + availableTiles.size() + " options\n");
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), msg.toString());

        if (!drawOnly) game.rebuildTilePositionAutoCompleteList();
    }

    private static List<TileModel> availableTiles(Game game, Set<ComponentSource> sources, String type, List<TileModel> drawnTiles) {
        Set<TileModel> existingTileModels = game.getTileMap().values().stream()
            .map(Tile::getTileModel).collect(Collectors.toSet());

        List<TileModel> availableTiles = new ArrayList<>();
        switch (type) {
          case "blue":
              availableTiles = TileHelper.getAllTileModels().stream()
                  .filter(tileModel -> TileBack.BLUE.equals(tileModel.getTileBack()))
                  .filter(tileModel -> sources.contains(tileModel.getSource()))
                  .filter(tileModel -> !existingTileModels.contains(tileModel))
                  .filter(tileModel -> !drawnTiles.contains(tileModel))
                  .collect(Collectors.toList());
              break;
          case "red":
              //Allow duplicate tiles if they are empty
              availableTiles = TileHelper.getAllTileModels().stream()
                  .filter(tileModel -> TileBack.RED.equals(tileModel.getTileBack()))
                  .filter(tileModel -> sources.contains(tileModel.getSource()))
                  .filter(tileModel -> tileModel.isEmpty() || (!existingTileModels.contains(tileModel) && !drawnTiles.contains(tileModel)))
                  .collect(Collectors.toList());
              break;
          case "green":
          case "hs":
              availableTiles = TileHelper.getAllTileModels().stream()
                  .filter(tileModel -> TileBack.GREEN.equals(tileModel.getTileBack()))
                  .filter(tileModel -> sources.contains(tileModel.getSource()))
                  .filter(tileModel -> !existingTileModels.contains(tileModel))
                  .filter(tileModel -> !drawnTiles.contains(tileModel))
                  .filter(tileModel -> new Tile(tileModel.getId(), "none").isHomeSystem())
                  .collect(Collectors.toList());
              break;
          case "hyperlane":
              availableTiles = TileHelper.getAllTileModels().stream()
                  .filter(tileModel -> tileModel.isHyperlane())
                  .collect(Collectors.toList());
              break;
        }
        return availableTiles;
    }
}
