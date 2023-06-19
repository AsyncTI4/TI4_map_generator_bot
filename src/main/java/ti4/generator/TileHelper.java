package ti4.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ti4.ResourceHelper;
import ti4.helpers.Storage;
import ti4.message.BotLogger;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class TileHelper {

    private static final java.util.Map<String, TileModel> allTiles = new HashMap<>();
    private static final java.util.Map<String, PlanetModel> allPlanets = new HashMap<>();

    public static void init() {
        initPlanetsFromJson();
        initTilesFromJson();
    }

    public static java.util.Map<String, PlanetModel> getAllPlanets() {
        return allPlanets;
    }

    public static java.util.Map<String, TileModel> getAllTiles() {
        return allTiles;
    }

    public static void initPlanetsFromJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        String path = Storage.getResourcePath() + File.separator + "planets" + File.separator;
        List<File> files = Stream.of(new File(path).listFiles())
                .filter(file -> !file.isDirectory())
                .toList();
        files.forEach(file -> {
                    try {
                        PlanetModel planet = objectMapper.readValue(new FileInputStream(file), PlanetModel.class);
                        allPlanets.put(planet.getId(), planet);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public static void initTilesFromJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        String path = Storage.getResourcePath() + File.separator + "systems" + File.separator;
        List<File> files = Stream.of(new File(path).listFiles())
                .filter(file -> !file.isDirectory())
                .toList();
        files.forEach(file -> {
            try {
                TileModel tile = objectMapper.readValue(new FileInputStream(file), TileModel.class);
                allTiles.put(tile.getId(), tile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
