package ti4.service.map;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;

@UtilityClass
public class AddTileService {

    public static void addTile(Game game, Tile tile) {
        game.removeTile(tile.getPosition()); //remove old tile first to clean up associated planet ownership
        game.setTile(tile);
        addCustodianToken(tile, game);
    }

    public static void addCustodianToken(Tile tile, Game game) {
        if (!tile.isMecatol() || game.isLiberationC4Mode()) {
            return;
        }
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        for (String mecatol : Constants.MECATOLS) {
            UnitHolder unitHolder = unitHolders.get(mecatol);
            if (unitHolder instanceof Planet && mecatol.equals(unitHolder.getName())) {
                unitHolder.addToken(Constants.CUSTODIAN_TOKEN_PNG);
            }
        }
    }

    public static Set<ComponentSource> getSources(SlashCommandInteractionEvent event, Game game) {
        return getSources(game, event.getOption(Constants.INCLUDE_ERONOUS_TILES, false, OptionMapping::getAsBoolean));
    }

    //This should be changed to support multiple sources and not just Eronous
    public static Set<ComponentSource> getSources(Game game, boolean eronousTiles) {
        Set<ComponentSource> sources = new HashSet<>();
        sources.add(ComponentSource.base);
        sources.add(ComponentSource.codex1);
        sources.add(ComponentSource.codex2);
        sources.add(ComponentSource.codex3);
        sources.add(ComponentSource.codex4);
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
            case BR:
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
            case B:
                availableTiles = findBlueTiles(sources, existingTileModels, drawnTiles);
                break;
            case R:
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
                    .filter(TileModel::isHyperlane)
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

    public enum RandomOption {
        B("Blue tile"), R("Red tile"), HS("Home System"), HL("Hyperlane"), BR("50/50 for Blue/Red tile");

        private final String description;

        RandomOption(String description) {
            this.description = description;
        }

        public String getAutoCompleteName() {
            return this + ": " + description;
        }

        public boolean search(String searchString) {
            return toString().toLowerCase().contains(searchString) || description.toLowerCase().contains(searchString);
        }

        public static boolean isValid(String value) {
            return EnumSet.allOf(RandomOption.class).stream().anyMatch(r -> value.equals(r.name()));
        }
    }
}
