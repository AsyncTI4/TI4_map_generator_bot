package ti4.image;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.collections4.CollectionUtils;

import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

public class TileHelper {

    private static final Map<String, TileModel> tileIdsToTileModels = new HashMap<>();
    private static final Map<String, PlanetModel> planetIdsToPlanetModels = new HashMap<>();
    private static final Map<String, List<PlanetModel>> tileIdsToPlanetModels = new HashMap<>();

    public static void init() {
        BotLogger.info("Initiating Planets");
        initPlanetsFromJson();
        BotLogger.info("Initiating Tiles");
        initTilesFromJson();
    }

    public static PlanetModel getPlanetById(String planetId) {
        return planetIdsToPlanetModels.get(planetId);
    }

    public static List<PlanetModel> getPlanetsByTileId(String tileId) {
        return tileIdsToPlanetModels.get(tileId);
    }

    public static TileModel getTileById(String tileId) {
        return tileIdsToTileModels.get(tileId);
    }

    public static Collection<String> getAllTileIds() {
        return tileIdsToTileModels.keySet();
    }

    public static Collection<TileModel> getAllTileModels() {
        return tileIdsToTileModels.values();
    }

    public static Collection<PlanetModel> getAllPlanetModels() {
        return planetIdsToPlanetModels.values();
    }

    public static void initPlanetsFromJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "planets" + File.separator;
        String storagePath = Storage.getStoragePath() + File.separator + "planets" + File.separator;
        List<File> files = new ArrayList<>();
        File[] storedFiles = new File(storagePath).listFiles();

        if (Optional.ofNullable(storedFiles).isPresent() && CollectionUtils.isNotEmpty(List.of(storedFiles))) {
            files.addAll(Stream.of(storedFiles)
                .filter(file -> !file.isDirectory())
                .toList());
        }
        files.addAll(Stream.of(new File(resourcePath).listFiles())
            .filter(file -> !file.isDirectory())
            .toList());

        List<String> badObjects = new ArrayList<>();
        files.forEach(file -> {
            try {
                PlanetModel planet = objectMapper.readValue(new FileInputStream(file), PlanetModel.class);
                planetIdsToPlanetModels.put(planet.getId(), planet);
                tileIdsToPlanetModels.computeIfAbsent(planet.getTileId(), k -> new ArrayList<>()).add(planet);
                if (!planet.isValid()) {
                    badObjects.add(planet.getAlias());
                }
            } catch (Exception e) {
                BotLogger.error("Error reading planet from file:\n> " + file.getPath(), e);
            }
        });
        if (!badObjects.isEmpty())
            BotLogger.warning("The following **PlanetModel** are improperly formatted, but were imported anyway:\n> "
                + String.join("\n> ", badObjects));
    }

    public static void initTilesFromJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "systems" + File.separator;
        String storagePath = Storage.getStoragePath() + File.separator + "systems" + File.separator;
        List<File> files = new ArrayList<>();
        File[] storedFiles = new File(storagePath).listFiles();

        if (Optional.ofNullable(storedFiles).isPresent() && CollectionUtils.isNotEmpty(List.of(storedFiles))) {
            files.addAll(Stream.of(storedFiles)
                .filter(File::exists)
                .filter(file -> !file.isDirectory())
                .toList());
        }
        files.addAll(Stream.of(new File(resourcePath).listFiles())
            .filter(file -> !file.isDirectory())
            .toList());
        List<String> badObjects = new ArrayList<>();
        files.forEach(file -> {
            try {
                TileModel tile = objectMapper.readValue(new FileInputStream(file), TileModel.class);
                tileIdsToTileModels.put(tile.getId(), tile);
                if (!tile.isValid()) {
                    badObjects.add(tile.getAlias());
                }

                if (isDraftTile(tile)) {
                    duplicateDraftTiles(tile);
                }
            } catch (Exception e) {
                //BotLogger.log("Error reading tile from file:\n> " + file.getPath(), e);
            }
        });
        if (!badObjects.isEmpty())
            BotLogger.warning("The following **TileModel** are improperly formatted, but were imported anyway:\n> "
                + String.join("\n> ", badObjects));
    }

    private static void duplicateDraftTiles(TileModel tile) {
        String color = tile.getAlias().replaceAll("blank", "");
        String namePre = Character.toUpperCase(color.charAt(0)) + color.substring(1).toLowerCase() + ", draft tile ";

        for (int i = 0; i < 13; i++) {
            TileModel newTile = new TileModel();
            newTile.setId(color + i);
            newTile.setName(namePre + i);
            newTile.setAliases(new ArrayList<>(List.of(color + i)));
            newTile.setImagePath(tile.getImagePath());
            newTile.setWormholes(Collections.emptySet());
            newTile.setPlanets(Collections.emptyList());
            newTile.setSource(tile.getSource());
            tileIdsToTileModels.put(newTile.getId(), newTile);
        }
    }

    public static boolean isDraftTile(TileModel tile) {
        return tile.getImagePath().startsWith("draft_");
    }

    public static void addNewTileToList(TileModel tile) {
        tileIdsToTileModels.put(tile.getId(), tile);
    }

    public static void addNewPlanetToList(PlanetModel planet) {
        planetIdsToPlanetModels.put(planet.getId(), planet);
    }

    public static void exportAllPlanets() {
        ObjectMapper mapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "planets" + File.separator;
        planetIdsToPlanetModels.values().forEach(planetModel -> {
            try {
                mapper.writeValue(new File(resourcePath + planetModel.getId() + ".json"), planetModel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void exportAllTiles() {
        ObjectMapper mapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "systems" + File.separator;
        tileIdsToTileModels.values().forEach(tileModel -> {
            try {
                mapper.writeValue(new File(resourcePath + tileModel.getId() + ".json"), tileModel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static boolean isValidTile(String tileID) {
        return tileIdsToTileModels.containsKey(tileID);
    }

    public static boolean isValidPlanet(String planetID) {
        return planetIdsToPlanetModels.containsKey(planetID);
    }

    public static Tile getTile(GenericInteractionCreateEvent event, String tileNameOrPos, Game game) {
        String tileAlias = AliasHandler.resolveTile(tileNameOrPos);
        if (game.isTileDuplicated(tileAlias)) {
            if (event != null)
                MessageHelper.replyToMessage(event, "Duplicate tile name `" + tileAlias + "` found, please use position coordinates");
            return null;
        }

        if (game.getTileByPosition(tileNameOrPos) != null) {
            return game.getTileByPosition(tileNameOrPos);
        }

        Tile tile = game.getTile(tileAlias);
        if (tile == null) {
            if (event != null)
                MessageHelper.replyToMessage(event, "Tile in map not found: " + tileNameOrPos);
            return null;
        }
        return tile;
    }
}
