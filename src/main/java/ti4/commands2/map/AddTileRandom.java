package ti4.commands2.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
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

public class AddTileRandom extends GameStateSubcommand {

    public enum RandomOption {
        BLUE("Blue tile"), 
        RED("Red tile"), 
        HS("Home System"), 
        HL("Hyperlane"), 
        ANY("50/50 for Blue/Red tile");

        private String description;
        private RandomOption(String description) {
            this.description = description;
        }
      
        public String getAutoCompleteName() {
            return toString() + ": " + description;
        }

        public boolean search(String searchString) {
            return toString().toLowerCase().contains(searchString) || description.toLowerCase().contains(searchString);
        }

        public static boolean isValid(String value) {
            return EnumSet.allOf(RandomOption.class).stream().anyMatch(r -> value.equals(r.name()));
        }
    }

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

        Set<ComponentSource> sources = getSources(event, game);

        List<String> drawnTiles = new ArrayList<>();
        StringBuffer msg = new StringBuffer();
        for (String position : positions) {
            Set<TileModel> existingTileModels = game.getTileMap().values().stream()
                .map(Tile::getTileModel).collect(Collectors.toSet());

            List<TileModel> availableTiles = availableTiles(sources, randomOption, existingTileModels, drawnTiles);
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

    //This should be changed to support multiple sources and not just Eronous
    public static Set<ComponentSource> getSources(SlashCommandInteractionEvent event, Game game) {
        boolean eronousTiles = event.getOption(Constants.INCLUDE_ERONOUS_TILES, false, OptionMapping::getAsBoolean);
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
        return sources;
    }

    public static List<TileModel> availableTiles(Set<ComponentSource> sources, RandomOption type, Set<TileModel> existingTileModels, List<String> drawnTiles) {
        List<TileModel> availableTiles = new ArrayList<>();
        switch (type) {
          case ANY:
              List<Supplier<List<TileModel>>> tileFinders = new Random().nextBoolean()
                  ? List.of(() -> findBlueTiles(sources, existingTileModels, drawnTiles),
                            () -> findRedTiles(sources, existingTileModels, drawnTiles))
                  : List.of(() -> findRedTiles(sources, existingTileModels, drawnTiles),
                            () -> findBlueTiles(sources, existingTileModels, drawnTiles));

              for (Supplier<List<TileModel>> tileFinder : tileFinders) {
                  availableTiles = tileFinder.get();
                  if (!availableTiles.isEmpty()) {
                      break;
                  }
              }
              break;
          case BLUE:
              availableTiles = findBlueTiles(sources, existingTileModels, drawnTiles);
              break;
          case RED:
              availableTiles = findRedTiles(sources, existingTileModels, drawnTiles);
              break;
          case HS:
              availableTiles = TileHelper.getAllTileModels().stream()
                  .filter(tileModel -> TileBack.GREEN.equals(tileModel.getTileBack()))
                  .filter(tileModel -> sources.contains(tileModel.getSource()))
                  .filter(tileModel -> !existingTileModels.contains(tileModel))
                  .filter(tileModel -> !drawnTiles.contains(tileModel.getId()))
                  .filter(tileModel -> new Tile(tileModel.getId(), "none").isHomeSystem())
                  .collect(Collectors.toList());
              break;
          case HL:
              availableTiles = TileHelper.getAllTileModels().stream()
                  .filter(tileModel -> tileModel.isHyperlane())
                  .collect(Collectors.toList());
              break;
        }
        return availableTiles;
    }

    private static List<TileModel> findBlueTiles(Set<ComponentSource> sources, Set<TileModel> existingTileModels, List<String> drawnTiles) {
        return TileHelper.getAllTileModels().stream()
            .filter(tileModel -> TileBack.BLUE.equals(tileModel.getTileBack()))
            .filter(tileModel -> sources.contains(tileModel.getSource()))
            .filter(tileModel -> !existingTileModels.contains(tileModel))
            .filter(tileModel -> !drawnTiles.contains(tileModel.getId()))
            .collect(Collectors.toList());
    }

    private static List<TileModel> findRedTiles(Set<ComponentSource> sources, Set<TileModel> existingTileModels, List<String> drawnTiles) {
        return TileHelper.getAllTileModels().stream()
            .filter(tileModel -> TileBack.RED.equals(tileModel.getTileBack()))
            .filter(tileModel -> sources.contains(tileModel.getSource()))
            //Allow duplicate tiles if they are empty
            .filter(tileModel -> tileModel.isEmpty() || (!existingTileModels.contains(tileModel) && !drawnTiles.contains(tileModel.getId())))
            .collect(Collectors.toList());
    }
}
