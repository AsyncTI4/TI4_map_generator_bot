package ti4.map;

import ti4.helpers.LoggerHandler;
import ti4.helpers.Storage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;

public class MapSaveLoadManager {

    public static void saveMaps() {
        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        for (java.util.Map.Entry<String, Map> mapEntry : mapList.entrySet()) {
            saveMap(mapEntry.getValue());
        }
    }

    public static void saveMap(Map map) {
        File mapFile = Storage.getMapImageStorage(map.getName() + ".txt");
        if (mapFile != null) {
            try (FileWriter writer = new FileWriter(mapFile.getAbsoluteFile())) {
                HashMap<String, Tile> tileMap = map.getTileMap();
                writer.write(map.getOwnerID());
                writer.write(map.getName());
                for (java.util.Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
                    Tile tile = tileEntry.getValue();
                    saveTile(writer, tile);
                }
            } catch (IOException e) {
                LoggerHandler.log("Could not save map: " + map.getName(), e);
            }
        } else {
            LoggerHandler.log("Could not save map, error creating save file");
        }
    }

    private static void saveTile(Writer writer, Tile tile) throws IOException {
        writer.write(tile.getTileID() + " " + tile.getPosition());
        //todo save units and other tokens
    }

    private File[] readAllMapFiles() {
        File folder = Storage.getMapImageDirectory();
        File[] listOfFiles = folder.listFiles();
    }

    public static void loadMaps() {

        HashMap<String, Map> mapList = new HashMap<>();

        for (java.util.Map.Entry<String, Map> mapEntry : mapList.entrySet()) {
            saveMap(mapEntry.getValue());
        }

        MapManager.getInstance().setMapList(mapList);
    }

    private static Map loadMap(File file) {
        File mapFile = Storage.getMapImageStorage(map.getName() + ".txt");
        if (mapFile != null) {
            try (FileWriter writer = new FileWriter(mapFile.getAbsoluteFile())) {
                HashMap<String, Tile> tileMap = map.getTileMap();
                writer.write(map.getOwnerID());
                writer.write(map.getName());
                for (java.util.Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
                    Tile tile = tileEntry.getValue();
                    saveTile(writer, tile);
                }
            } catch (IOException e) {
                LoggerHandler.log("Could not save map: " + map.getName(), e);
            }
        } else {
            LoggerHandler.log("Could not save map, error creating save file");
        }
    }

}
