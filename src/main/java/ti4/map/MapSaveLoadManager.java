package ti4.map;

import ti4.helpers.LoggerHandler;
import ti4.helpers.Storage;

import javax.annotation.CheckForNull;
import java.io.*;
import java.util.HashMap;
import java.util.Scanner;
import java.util.StringTokenizer;

public class MapSaveLoadManager {

    public static final String TXT = ".txt";
    public static final String TILE = "-tile-";
    public static final String UNITS = "-units-";
    public static final String ENDUNITS = "-endunits-";
    public static final String ENDTILE = "-endtile-";
    public static final String TOKENS = "-tokens-";
    public static final String ENDTOKENS = "-endtokens-";

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
                writer.write(System.lineSeparator());
                writer.write(map.getName());
                writer.write(System.lineSeparator());
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
        writer.write(TILE);
        writer.write(System.lineSeparator());
        writer.write(tile.getTileID() + " " + tile.getPosition());
        writer.write(System.lineSeparator());
        writer.write(UNITS);
        writer.write(System.lineSeparator());
        HashMap<String, String> units = tile.getUnits();
        for (java.util.Map.Entry<String, String> entry : units.entrySet()) {
            writer.write(entry.getKey() + " " + entry.getValue());
            writer.write(System.lineSeparator());
        }
        writer.write(ENDUNITS);
        writer.write(System.lineSeparator());

        writer.write(TOKENS);
        writer.write(System.lineSeparator());

        writer.write(ENDTOKENS);
        writer.write(System.lineSeparator());
        writer.write(ENDTILE);
        writer.write(System.lineSeparator());
    }

    private static File[] readAllMapFiles() {
        File folder = Storage.getMapImageDirectory();
        if (folder == null) {
            try {
                //noinspection ConstantConditions
                if (folder.createNewFile()) {
                    folder = Storage.getMapImageDirectory();
                }
            } catch (IOException e) {
                LoggerHandler.log("Could not create folder for maps");
            }

        }
        return folder.listFiles();
    }

    private static boolean isTxtExtention(File file) {
        return file.getAbsolutePath().endsWith(TXT);

    }

    public static boolean deleteMap(String mapName) {
        File mapStorage = Storage.getMapStorage(mapName+ TXT);
        if (mapStorage == null) {
            return false;
        }
        File deletedMapStorage = Storage.getDeletedMapStorage(mapName + "_" + System.currentTimeMillis() + TXT);
        return mapStorage.renameTo(deletedMapStorage);
    }

    public static void loadMaps() {
        HashMap<String, Map> mapList = new HashMap<>();
        File[] files = readAllMapFiles();
        if (files != null) {
            for (File file : files) {
                if (isTxtExtention(file)) {
                    Map map = loadMap(file);
                    if (map != null) {
                        mapList.put(map.getName(), map);
                    }
                }
            }
        }
        MapManager.getInstance().setMapList(mapList);
    }

    @CheckForNull
    private static Map loadMap(File mapFile) {
        if (mapFile != null) {
            Map map = new Map();
            try (Scanner myReader = new Scanner(mapFile)) {
                map.setOwnerID(myReader.nextLine());
                map.setName(myReader.nextLine());
                HashMap<String, Tile> tileMap = new HashMap<>();
                while (myReader.hasNextLine()) {
                    String tileData = myReader.nextLine();
                    if (TILE.equals(tileData)) {
                        continue;
                    }
                    if (ENDTILE.equals(tileData)) {
                        continue;
                    }
                    Tile tile = readTile(tileData);
                    tileMap.put(tile.getPosition(), tile);

                    while (myReader.hasNextLine()) {
                        String data = myReader.nextLine();
                        if (UNITS.equals(data)) {
                            continue;
                        }
                        if (ENDUNITS.equals(data)) {
                            break;
                        }
                        readUnit(tile, data);
                    }

                    while (myReader.hasNextLine()) {
                        String data = myReader.nextLine();
                        if (TOKENS.equals(data)) {
                            continue;
                        }
                        if (ENDTOKENS.equals(data)) {
                            break;
                        }
                        readTokens(tile, data);
                    }


                }
                map.setTileMap(tileMap);
            } catch (FileNotFoundException e) {
                LoggerHandler.log("File not found to read map data: " + mapFile.getName(), e);
            }
            return map;
        } else {
            LoggerHandler.log("Could not save map, error creating save file");
        }
        return null;
    }

    private static Tile readTile(String tileData) {
        StringTokenizer tokenizer = new StringTokenizer(tileData, " ");
        return new Tile(tokenizer.nextToken(), tokenizer.nextToken());
    }

    private static void readUnit(Tile tile, String data) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        tile.setUnit(tokenizer.nextToken(), tokenizer.nextToken());
    }

    private static void readTokens(Tile tile, String data) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
//        tile.setUnit(tokenizer.nextToken(), tokenizer.nextToken());
        //todo implement token read
    }

}
