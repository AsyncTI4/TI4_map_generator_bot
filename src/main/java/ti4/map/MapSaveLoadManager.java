package ti4.map;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Role;

import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.message.BotLogger;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class MapSaveLoadManager {

    public static final String TXT = ".txt";
    public static final String TILE = "-tile-";
    public static final String UNITS = "-units-";
    public static final String UNITHOLDER = "-unitholder-";
    public static final String ENDUNITHOLDER = "-endunitholder-";
    public static final String ENDUNITS = "-endunits-";
    public static final String ENDUNITDAMAGE = "-endunitdamage-";
    public static final String UNITDAMAGE = "-unitdamage-";
    public static final String ENDTILE = "-endtile-";
    public static final String TOKENS = "-tokens-";
    public static final String ENDTOKENS = "-endtokens-";
    public static final String PLANET_TOKENS = "-planettokens-";
    public static final String PLANET_ENDTOKENS = "-planetendtokens-";

    public static final String MAPINFO = "-mapinfo-";
    public static final String ENDMAPINFO = "-endmapinfo-";
    public static final String GAMEINFO = "-gameinfo-";
    public static final String ENDGAMEINFO = "-endgameinfo-";
    public static final String PLAYERINFO = "-playerinfo-";
    public static final String ENDPLAYERINFO = "-endplayerinfo-";
    public static final String PLAYER = "-player-";
    public static final String ENDPLAYER = "-endplayer-";

    public static void saveMaps() {
        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        for (java.util.Map.Entry<String, Map> mapEntry : mapList.entrySet()) {
            saveMap(mapEntry.getValue(), true);
        }
    }
    public static void saveMap(Map map) {
        saveMap(map, false);
    }
    public static void saveMap(Map map, boolean keepModifiedDate) {
        File mapFile = Storage.getMapImageStorage(map.getName() + TXT);
        if (mapFile != null) {
            saveUndo(map, mapFile);
            try (FileWriter writer = new FileWriter(mapFile.getAbsoluteFile())) {
                HashMap<String, Tile> tileMap = map.getTileMap();
                writer.write(map.getOwnerID());
                writer.write(map.getOwnerID());
                writer.write(System.lineSeparator());
                writer.write(map.getOwnerName());
                writer.write(System.lineSeparator());
                writer.write(map.getName());
                writer.write(System.lineSeparator());
                saveMapInfo(writer, map, keepModifiedDate);

                for (java.util.Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
                    Tile tile = tileEntry.getValue();
                    saveTile(writer, tile);
                }
            } catch (IOException e) {
                BotLogger.log("Could not save map: " + map.getName());
            }
        } else {
            BotLogger.log("Could not save map, error creating save file");
        }
    }

    public static void undo(Map map) {
        File originalMapFile = Storage.getMapImageStorage(map.getName() + Constants.TXT);
        if (originalMapFile != null) {
            File mapUndoDirectory = Storage.getMapUndoDirectory();
            if (mapUndoDirectory == null) {
                return;
            }
            if (!mapUndoDirectory.exists()) {
                return;
            }

            String mapName = map.getName();
            String mapNameForUndoStart = mapName + "_";
            String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
            if (mapUndoFiles != null && mapUndoFiles.length > 0) {
                try {
                    List<Integer> numbers = Arrays.stream(mapUndoFiles)
                            .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                            .map(fileName -> fileName.replace(Constants.TXT, ""))
                            .map(Integer::parseInt).toList();
                    Integer maxNumber = numbers.isEmpty() ? 0 : numbers.stream().mapToInt(value -> value)
                            .max().orElseThrow(NoSuchElementException::new);
                    File mapUndoStorage = Storage.getMapUndoStorage(mapName + "_" + maxNumber + Constants.TXT);
                    CopyOption[] options = {StandardCopyOption.REPLACE_EXISTING};
                    Files.copy(mapUndoStorage.toPath(), originalMapFile.toPath(), options);
                    mapUndoStorage.delete();
//                    reload(map);
                    Map loadedMap = loadMap(originalMapFile);
                    MapManager.getInstance().deleteMap(map.getName());
                    MapManager.getInstance().addMap(loadedMap);
                } catch (Exception e) {
                    BotLogger.log("Error trying to make undo copy for map: " + mapName);
                }
            }
        }
    }

    public static void reload(Map map) {
        File originalMapFile = Storage.getMapImageStorage(map.getName() + Constants.TXT);
        if (originalMapFile != null) {
            Map loadedMap = loadMap(originalMapFile);
            MapManager.getInstance().deleteMap(map.getName());
            MapManager.getInstance().addMap(loadedMap);
        }
    }

    private static void saveUndo(Map map, File originalMapFile) {
        File mapUndoDirectory = Storage.getMapUndoDirectory();
        if (mapUndoDirectory == null) {
            return;
        }
        if (!mapUndoDirectory.exists()) {
            mapUndoDirectory.mkdir();
        }

        String mapName = map.getName();
        String mapNameForUndoStart = mapName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
        if (mapUndoFiles != null) {
            try {
                List<Integer> numbers = Arrays.stream(mapUndoFiles)
                        .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                        .map(fileName -> fileName.replace(Constants.TXT, ""))
                        .map(Integer::parseInt).toList();
                if (numbers.size() == 10) {
                    int minNumber = numbers.stream().mapToInt(value -> value)
                            .min().orElseThrow(NoSuchElementException::new);
                    File mapToDelete = Storage.getMapUndoStorage(mapName + "_" + minNumber + Constants.TXT);
                    mapToDelete.delete();
                }
                Integer maxNumber = numbers.isEmpty() ? 0 : numbers.stream().mapToInt(value -> value)
                        .max().orElseThrow(NoSuchElementException::new);
                maxNumber++;
                File mapUndoStorage = Storage.getMapUndoStorage(mapName + "_" + maxNumber + Constants.TXT);
                CopyOption[] options = {StandardCopyOption.REPLACE_EXISTING};
                Files.copy(originalMapFile.toPath(), mapUndoStorage.toPath(), options);
            } catch (Exception e) {
                BotLogger.log("Error trying to make undo copy for map: " + mapName);
            }
        }
    }

    private static void saveMapInfo(FileWriter writer, Map map, boolean keepModifiedDate) throws IOException {
        writer.write(MAPINFO);
        writer.write(System.lineSeparator());

        writer.write(map.getMapStatus());
        writer.write(System.lineSeparator());

        writer.write(GAMEINFO);
        writer.write(System.lineSeparator());
        //game information
        writer.write(Constants.SO + " " + String.join(",", map.getSecretObjectives()));
        writer.write(System.lineSeparator());

        writer.write(Constants.AC + " " + String.join(",", map.getActionCards()));
        writer.write(System.lineSeparator());

        writeCards(map.getDiscardActionCards(), writer, Constants.AC_DISCARDED);

        writer.write(Constants.EXPLORE + " " + String.join(",", map.getAllExplores()));
        writer.write(System.lineSeparator());

        writer.write(Constants.RELICS + " " + String.join(",", map.getAllRelics()));
        writer.write(System.lineSeparator());

        writer.write(Constants.DISCARDED_EXPLORES + " " + String.join(",", map.getAllExploreDiscard()));
        writer.write(System.lineSeparator());

        writer.write(Constants.SPEAKER + " " + map.getSpeaker());
        writer.write(System.lineSeparator());

        HashMap<Integer, Boolean> scPlayed = map.getScPlayed();
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Integer, Boolean> entry : scPlayed.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.SC_PLAYED + " " + sb);
        writer.write(System.lineSeparator());

        writer.write(Constants.AGENDAS + " " + String.join(",", map.getAgendas()));
        writer.write(System.lineSeparator());

        writeCards(map.getDiscardAgendas(), writer, Constants.DISCARDED_AGENDAS);
        writeCards(map.getSentAgendas(), writer, Constants.SENT_AGENDAS);
        writeCards(map.getLaws(), writer, Constants.LAW);

        sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : map.getLawsInfo().entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.LAW_INFO + " " + sb);
        writer.write(System.lineSeparator());

        sb = new StringBuilder();
        for (java.util.Map.Entry<Integer, Integer> entry : map.getScTradeGoods().entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.SC_TRADE_GOODS + " " + sb);
        writer.write(System.lineSeparator());

        writeCards(map.getRevealedPublicObjectives(), writer, Constants.REVEALED_PO);
        writeCards(map.getCustomPublicVP(), writer, Constants.CUSTOM_PO_VP);
        writer.write(Constants.PO1 + " " + String.join(",", map.getPublicObjectives1()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PO2 + " " + String.join(",", map.getPublicObjectives2()));
        writer.write(System.lineSeparator());
        writer.write(Constants.SO_TO_PO + " " + String.join(",", map.getSoToPoList()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PURGED_PN + " " + String.join(",", map.getPurgedPN()));
        writer.write(System.lineSeparator());

        DisplayType displayTypeForced = map.getDisplayTypeForced();
        if (displayTypeForced != null) {
            writer.write(Constants.DISPLAY_TYPE + " " + displayTypeForced.getValue());
            writer.write(System.lineSeparator());
        }

        writer.write(Constants.PLAYER_COUNT_FOR_MAP + " " + map.getPlayerCountForMap());
        writer.write(System.lineSeparator());

        writer.write(Constants.VP_COUNT + " " + map.getVp());
        writer.write(System.lineSeparator());

        StringBuilder sb1 = new StringBuilder();
        for (java.util.Map.Entry<String, List<String>> entry : map.getScoredPublicObjectives().entrySet()) {
            String userIds = String.join("-", entry.getValue());
            sb1.append(entry.getKey()).append(",").append(userIds).append(";");
        }
        writer.write(Constants.SCORED_PO + " " + sb1);
        writer.write(System.lineSeparator());

        StringBuilder adjacentTiles = new StringBuilder();
        for (java.util.Map.Entry<String, List<String>> entry : map.getCustomAdjacentTiles().entrySet()) {
            String userIds = String.join("-", entry.getValue());
            adjacentTiles.append(entry.getKey()).append(",").append(userIds).append(";");
        }
        writer.write(Constants.CUSTOM_ADJACENT_TILES + " " + adjacentTiles);
        writer.write(System.lineSeparator());

        writer.write(Constants.CREATION_DATE + " " + map.getCreationDate());
        writer.write(System.lineSeparator());
        long time = keepModifiedDate ? map.getLastModifiedDate() : new Date().getTime();
        writer.write(Constants.LAST_MODIFIED_DATE + " " + time);
        writer.write(System.lineSeparator());
        writer.write(Constants.ROUND + " " + map.getRound());
        writer.write(System.lineSeparator());
        writer.write(Constants.GAME_CUSTOM_NAME + " " + map.getCustomName());
        writer.write(System.lineSeparator());
        writer.write(Constants.COMMUNITY_MODE + " " + map.isCommunityMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.ALLIANCE_MODE + " " + map.isAllianceMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.FOW_MODE + " " + map.isFoWMode());
        writer.write(System.lineSeparator());

        writer.write(ENDGAMEINFO);
        writer.write(System.lineSeparator());

        //Player information
        writer.write(PLAYERINFO);
        writer.write(System.lineSeparator());
        HashMap<String, Player> players = map.getPlayers();
        for (java.util.Map.Entry<String, Player> playerEntry : players.entrySet()) {
            writer.write(PLAYER);
            writer.write(System.lineSeparator());

            Player player = playerEntry.getValue();
            writer.write(player.getUserID());
            writer.write(System.lineSeparator());
            writer.write(player.getUserName());
            writer.write(System.lineSeparator());

            writer.write(Constants.FACTION + " " + player.getFaction());
            writer.write(System.lineSeparator());
            //TODO: Remove when no longer relevant
            String playerColor = player.getColor();
            if(player.getFaction() == null || "null".equals(player.getFaction())) {
                playerColor = "null";
            }
            writer.write(Constants.COLOR + " " + playerColor);
            writer.write(System.lineSeparator());

            Role roleForCommunity = player.getRoleForCommunity();
            if (roleForCommunity != null) {
                writer.write(Constants.ROLE_FOR_COMMUNITY + " " + roleForCommunity.getId());
                writer.write(System.lineSeparator());
            }

            Channel channelForCommunity = player.getChannelForCommunity();
            if (channelForCommunity != null) {
                writer.write(Constants.CHANNLE_FOR_COMMUNITY + " " + channelForCommunity.getId());
                writer.write(System.lineSeparator());
            }

            writer.write(Constants.PASSED + " " + player.isPassed());
            writer.write(System.lineSeparator());

            writer.write(Constants.SEARCH_WARRANT + " " + player.isSearchWarrant());
            writer.write(System.lineSeparator());

            writer.write(Constants.DUMMY + " " + player.isDummy());
            writer.write(System.lineSeparator());

            writeCards(player.getActionCards(), writer, Constants.AC);
            writeCards(player.getPromissoryNotes(), writer, Constants.PROMISSORY_NOTES);
            writer.write(Constants.PROMISSORY_NOTES_PLAY_AREA + " " + String.join(",", player.getPromissoryNotesInPlayArea()));
            writer.write(System.lineSeparator());

            writer.write(Constants.FRAGMENTS + " " + String.join(",", player.getFragments()));
            writer.write(System.lineSeparator());

            writer.write(Constants.RELICS + " " + String.join(",", player.getRelics()));
            writer.write(System.lineSeparator());

            writer.write(Constants.EXHAUSTED_RELICS + " " + String.join(",", player.getExhaustedRelics()));
            writer.write(System.lineSeparator());

            writer.write(Constants.MAHACT_CC + " " + String.join(",", player.getMahactCC()));
            writer.write(System.lineSeparator());

            writer.write(Constants.TECH + " " + String.join(",", player.getTechs()));
            writer.write(System.lineSeparator());
            writer.write(Constants.TECH_EXHAUSTED + " " + String.join(",", player.getExhaustedTechs()));
            writer.write(System.lineSeparator());

            writer.write(Constants.PLANETS + " " + String.join(",", player.getPlanets()));
            writer.write(System.lineSeparator());
            writer.write(Constants.PLANETS_EXHAUSTED + " " + String.join(",", player.getExhaustedPlanets()));
            writer.write(System.lineSeparator());
            writer.write(Constants.PLANETS_ABILITY_EXHAUSTED + " " + String.join(",", player.getExhaustedPlanetsAbilities()));
            writer.write(System.lineSeparator());

            writer.write(Constants.TACTICAL + " " + player.getTacticalCC());
            writer.write(System.lineSeparator());
            writer.write(Constants.FLEET + " " + player.getFleetCC());
            writer.write(System.lineSeparator());
            writer.write(Constants.STRATEGY + " " + player.getStrategicCC());
            writer.write(System.lineSeparator());

            writer.write(Constants.TG + " " + player.getTg());
            writer.write(System.lineSeparator());
            writer.write(Constants.COMMODITIES + " " + player.getCommodities());
            writer.write(System.lineSeparator());
            writer.write(Constants.COMMODITIES_TOTAL + " " + player.getCommoditiesTotal());
            writer.write(System.lineSeparator());

            writer.write(Constants.SO + " " + getSecretList(player.getSecrets()));
            writer.write(System.lineSeparator());
            writer.write(Constants.SO_SCORED + " " + getSecretList(player.getSecretsScored()));
            writer.write(System.lineSeparator());
            writer.write(Constants.STRATEGY_CARD + " " + player.getSC());
            writer.write(System.lineSeparator());

            StringBuilder leaderInfo = new StringBuilder();
            for (Leader leader : player.getLeaders()) {
                leaderInfo.append(leader.getId());
                leaderInfo.append(",");
                String name = leader.getName();
                leaderInfo.append(name.isEmpty() ? "." : name);
                leaderInfo.append(",");
                leaderInfo.append(leader.getTgCount());
                leaderInfo.append(",");
                leaderInfo.append(leader.isExhausted());
                leaderInfo.append(",");
                leaderInfo.append(leader.isLocked());
                leaderInfo.append(",");
                leaderInfo.append(leader.isActive());
                leaderInfo.append(";");
            }
            writer.write(Constants.LEADERS + " " + leaderInfo);
            writer.write(System.lineSeparator());

            StringBuilder fogOfWarSystems = new StringBuilder();
            HashMap<String,String> fow_systems = player.getFogFilter();
            HashMap<String,String> fow_labels = player.getFogLabels();
            for (String key : fow_systems.keySet()) {
                String system = fow_systems.get(key);
                String label = fow_labels.get(key);
                fogOfWarSystems.append(key);
                fogOfWarSystems.append(",");
                fogOfWarSystems.append(system);
                fogOfWarSystems.append(",");
                fogOfWarSystems.append(label == null || label.equals("") ? "." : label);
                fogOfWarSystems.append(";");
            }
            writer.write(Constants.FOW_SYSTEMS + " " + fogOfWarSystems);
            writer.write(System.lineSeparator());

            writer.write(ENDPLAYER);
            writer.write(System.lineSeparator());
        }

        writer.write(ENDPLAYERINFO);
        writer.write(System.lineSeparator());


        writer.write(ENDMAPINFO);
        writer.write(System.lineSeparator());
    }

    private static void writeCards(LinkedHashMap<String, Integer> cardList, FileWriter writer, String saveID) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> entry : cardList.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(saveID + " " + sb);
        writer.write(System.lineSeparator());
    }

    private static String getSecretList(LinkedHashMap<String, Integer> secrets) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
            sb.append(so.getKey()).append(",").append(so.getValue()).append(";");
        }
        return sb.toString();
    }

    private static void saveTile(Writer writer, Tile tile) throws IOException {
        writer.write(TILE);
        writer.write(System.lineSeparator());
        writer.write(tile.getTileID() + " " + tile.getPosition());
        writer.write(System.lineSeparator());
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
        writer.write(UNITHOLDER);
        writer.write(System.lineSeparator());
        for (UnitHolder unitHolder : unitHolders.values()) {
            writer.write(UNITS);
            writer.write(System.lineSeparator());
            writer.write(unitHolder.getName());
            writer.write(System.lineSeparator());
            HashMap<String, Integer> units = unitHolder.getUnits();
            for (java.util.Map.Entry<String, Integer> entry : units.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.write(System.lineSeparator());
            }
            writer.write(ENDUNITS);
            writer.write(System.lineSeparator());

            writer.write(UNITDAMAGE);
            writer.write(System.lineSeparator());
            HashMap<String, Integer> unitDamage = unitHolder.getUnitDamage();
            for (java.util.Map.Entry<String, Integer> entry : unitDamage.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.write(System.lineSeparator());
            }
            writer.write(ENDUNITDAMAGE);
            writer.write(System.lineSeparator());

            writer.write(PLANET_TOKENS);
            writer.write(System.lineSeparator());
            for (String ccID : unitHolder.getCCList()) {
                writer.write(ccID);
                writer.write(System.lineSeparator());
            }

            for (String controlID : unitHolder.getControlList()) {
                writer.write(controlID);
                writer.write(System.lineSeparator());
            }

            for (String tokenID : unitHolder.getTokenList()) {
                writer.write(tokenID);
                writer.write(System.lineSeparator());
            }
            writer.write(PLANET_ENDTOKENS);
            writer.write(System.lineSeparator());
        }
        writer.write(ENDUNITHOLDER);
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
                BotLogger.log("Could not create folder for maps");
            }

        }
        return folder.listFiles();
    }

    private static boolean isTxtExtention(File file) {
        return file.getAbsolutePath().endsWith(TXT);

    }

    public static boolean deleteMap(String mapName) {
        File mapStorage = Storage.getMapStorage(mapName + TXT);
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
                    try {
                        Map map = loadMap(file);
                        if (map != null) {
                            mapList.put(map.getName(), map);
                        }
                    } catch (Exception e) {
                        BotLogger.log("Could not load game:" + file);
                    }
                }
            }
        }
        MapManager.getInstance().setMapList(mapList);
    }

    @Nullable
    private static Map loadMap(File mapFile) {
        if (mapFile != null) {
            Map map = new Map();
            try (Scanner myReader = new Scanner(mapFile)) {
                map.setOwnerID(myReader.nextLine());
                map.setOwnerName(myReader.nextLine());
                map.setName(myReader.nextLine());
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    if (MAPINFO.equals(data)) {
                        continue;
                    }
                    if (ENDMAPINFO.equals(data)) {
                        break;
                    }
                    map.setMapStatus(MapStatus.valueOf(data));

                    while (myReader.hasNextLine()) {
                        data = myReader.nextLine();
                        if (GAMEINFO.equals(data)) {
                            continue;
                        }
                        if (ENDGAMEINFO.equals(data)) {
                            break;
                        }
                        try {
                            readGameInfo(map, data);
                        } catch (Exception e) {
                            BotLogger.log("Data is bad: " + map.getName());
                        }
                    }

                    while (myReader.hasNextLine()) {
                        String tmpData = myReader.nextLine();
                        if (PLAYERINFO.equals(tmpData)) {
                            continue;
                        }
                        if (ENDPLAYERINFO.equals(tmpData)) {
                            break;
                        }
                        Player player = null;
                        while (myReader.hasNextLine()) {
                            data = tmpData != null ? tmpData : myReader.nextLine();
                            tmpData = null;
                            if (PLAYER.equals(data)) {

                                player = map.addPlayerLoad(myReader.nextLine(), myReader.nextLine());
                                continue;
                            }
                            if (ENDPLAYER.equals(data)) {
                                break;
                            }
                            readPlayerInfo(player, data);
                        }
                    }
                }
                HashMap<String, Tile> tileMap = new HashMap<>();
                try {
                    while (myReader.hasNextLine()) {
                        String tileData = myReader.nextLine();
                        if (TILE.equals(tileData)) {
                            continue;
                        }
                        if (ENDTILE.equals(tileData)) {
                            continue;
                        }
                        if (tileData.isEmpty()) {
                            continue;
                        }
                        Tile tile = readTile(tileData);
                        tileMap.put(tile.getPosition(), tile);

                        while (myReader.hasNextLine()) {
                            String tmpData = myReader.nextLine();
                            if (UNITHOLDER.equals(tmpData)) {
                                continue;
                            }
                            if (ENDUNITHOLDER.equals(tmpData)) {
                                break;
                            }
                            String spaceHolder = null;
                            while (myReader.hasNextLine()) {
                                String data = tmpData != null ? tmpData : myReader.nextLine();
                                tmpData = null;
                                if (UNITS.equals(data)) {
                                    spaceHolder = myReader.nextLine();
                                    if (Constants.MIRAGE.equals(spaceHolder)) {
                                        Helper.addMirageToTile(tile);
                                    } else if (!tile.isSpaceHolderValid(spaceHolder)) {
                                        BotLogger.log(map.getName() + ": Not valid space holder detected: " + spaceHolder);
                                    }
                                    continue;
                                }
                                if (ENDUNITS.equals(data)) {
                                    break;
                                }
                                readUnit(tile, data, spaceHolder);
                            }

                            while (myReader.hasNextLine()) {
                                String data = myReader.nextLine();
                                if (UNITDAMAGE.equals(data)) {
                                    continue;
                                }
                                if (ENDUNITDAMAGE.equals(data)) {
                                    break;
                                }
                                readUnitDamage(tile, data, spaceHolder);
                            }

                            while (myReader.hasNextLine()) {
                                String data = myReader.nextLine();
                                if (PLANET_TOKENS.equals(data)) {
                                    continue;
                                }
                                if (PLANET_ENDTOKENS.equals(data)) {
                                    break;
                                }
                                readPlanetTokens(tile, data, spaceHolder);
                            }
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
                } catch (Exception e) {
                    BotLogger.log("Data read error: " + mapFile.getName());
                }
                map.setTileMap(tileMap);
            } catch (FileNotFoundException e) {
                BotLogger.log("File not found to read map data: " + mapFile.getName());
            } catch (Exception e) {
                BotLogger.log("Data read error: " + mapFile.getName());
            }
            return map;
        } else {
            BotLogger.log("Could not save map, error creating save file");
        }
        return null;
    }

    private static void readGameInfo(Map map, String data) {
        String[] tokenizer = data.split(" ", 2);
        if (tokenizer.length == 2) {
            String identification = tokenizer[0];
            if (Constants.SO.equals(identification)) {
                map.setSecretObjectives(getCardList(tokenizer[1]));
            } else if (Constants.AC.equals(identification)) {
                map.setActionCards(getCardList(tokenizer[1]));
            } else if (Constants.PO1.equals(identification)) {
                map.setPublicObjectives1(getCardList(tokenizer[1]));
            } else if (Constants.PO2.equals(identification)) {
                map.setPublicObjectives2(getCardList(tokenizer[1]));
            } else if (Constants.SO_TO_PO.equals(identification)) {
                map.setSoToPoList(getCardList(tokenizer[1]));
            } else if (Constants.PURGED_PN.equals(identification)) {
                map.setPurgedPNs(getCardList(tokenizer[1]));
            } else if (Constants.REVEALED_PO.equals(identification)) {
                map.setRevealedPublicObjectives(getParsedCards(tokenizer[1]));
                //temp code to migrate round numbers
//                Set<String> strings = new HashSet<>(map.getRevealedPublicObjectives().keySet());
//                Set<String> strings2 = new HashSet<>(map.getRevealedPublicObjectives().keySet());
//                strings.retainAll(map.getPublicObjectives1());
//                strings2.retainAll(map.getPublicObjectives2());
//                map.setRound((strings.size() + strings2.size())-1);
            } else if (Constants.CUSTOM_PO_VP.equals(identification)) {
                map.setCustomPublicVP(getParsedCards(tokenizer[1]));
            } else if (Constants.SCORED_PO.equals(identification)) {
                map.setScoredPublicObjectives(getParsedCardsForScoredPO(tokenizer[1]));
            } else if (Constants.CUSTOM_ADJACENT_TILES.equals(identification)) {
                map.setCustomAdjacentTiles(getParsedCardsForScoredPO(tokenizer[1]));
            } else if (Constants.AGENDAS.equals(identification)) {
                map.setAgendas(getCardList(tokenizer[1]));
            } else if (Constants.AC_DISCARDED.equals(identification)) {
                map.setDiscardActionCards(getParsedCards(tokenizer[1]));
            } else if (Constants.DISCARDED_AGENDAS.equals(identification)) {
                map.setDiscardAgendas(getParsedCards(tokenizer[1]));
            } else if (Constants.SENT_AGENDAS.equals(identification)) {
                map.setSentAgendas(getParsedCards(tokenizer[1]));
            } else if (Constants.LAW.equals(identification)) {
                map.setLaws(getParsedCards(tokenizer[1]));
            } else if (Constants.EXPLORE.equals(identification)) {
                map.setExploreDeck(getCardList(tokenizer[1]));
            } else if (Constants.RELICS.equals(identification)) {
                map.setRelics(getCardList(tokenizer[1]));
            } else if (Constants.DISCARDED_EXPLORES.equals(identification)) {
                map.setExploreDiscard(getCardList(tokenizer[1]));
            } else if (Constants.LAW_INFO.equals(identification)) {
                StringTokenizer actionCardToken = new StringTokenizer(tokenizer[1], ";");
                LinkedHashMap<String, String> cards = new LinkedHashMap<>();
                while (actionCardToken.hasMoreTokens()) {
                    StringTokenizer cardInfo = new StringTokenizer(actionCardToken.nextToken(), ",");
                    String id = cardInfo.nextToken();
                    String value = cardInfo.nextToken();
                    cards.put(id, value);
                }
                map.setLawsInfo(cards);
            } else if (Constants.SC_TRADE_GOODS.equals(identification)) {
                StringTokenizer scTokenizer = new StringTokenizer(tokenizer[1], ";");
                while (scTokenizer.hasMoreTokens()) {
                    StringTokenizer cardInfo = new StringTokenizer(scTokenizer.nextToken(), ",");
                    Integer id = Integer.parseInt(cardInfo.nextToken());
                    Integer value = Integer.parseInt(cardInfo.nextToken());
                    map.setScTradeGood(id, value);
                }
            } else if (Constants.SPEAKER.equals(identification)) {
                map.setSpeaker(tokenizer[1]);
            } else if (Constants.PLAYER_COUNT_FOR_MAP.equals(identification)) {
                String count = tokenizer[1];
                try {
                    int playerCount = Integer.parseInt(count);
                    if (playerCount == 6 || playerCount == 8) {
                        map.setPlayerCountForMap(playerCount);
                    } else {
                        map.setPlayerCountForMap(6);
                    }
                } catch (Exception e) {
                    map.setPlayerCountForMap(6);
                }
            } else if (Constants.VP_COUNT.equals(identification)) {
                String count = tokenizer[1];
                try {
                    int vpCount = Integer.parseInt(count);
                    map.setVp(vpCount);
                } catch (Exception e) {
                    map.setVp(10);
                }
            } else if (Constants.DISPLAY_TYPE.equals(identification)) {
                String displayType = tokenizer[1];
                if (displayType.equals(DisplayType.stats.getValue())) {
                    map.setDisplayTypeForced(DisplayType.stats);
                } else if (displayType.equals(DisplayType.map.getValue())) {
                    map.setDisplayTypeForced(DisplayType.map);
                } else if (displayType.equals(DisplayType.all.getValue())) {
                    map.setDisplayTypeForced(DisplayType.all);
                }

            } else if (Constants.SC_PLAYED.equals(identification)) {
                StringTokenizer scPlayed = new StringTokenizer(tokenizer[1], ";");
                while (scPlayed.hasMoreTokens()) {
                    StringTokenizer dataInfo = new StringTokenizer(scPlayed.nextToken(), ",");
                    Integer scID = Integer.parseInt(dataInfo.nextToken());
                    Boolean status = Boolean.parseBoolean(dataInfo.nextToken());
                    map.setSCPlayed(scID, status);
                }
            } else if (Constants.GAME_CUSTOM_NAME.equals(identification)) {
                map.setCustomName(tokenizer[1]);
            } else if (Constants.COMMUNITY_MODE.equals(identification)) {
                try {
                    boolean value = Boolean.parseBoolean(tokenizer[1]);
                    map.setCommunityMode(value);
                } catch (Exception e) {
                    //Do nothing
                }
            } else if (Constants.ALLIANCE_MODE.equals(identification)) {
                try {
                    boolean value = Boolean.parseBoolean(tokenizer[1]);
                    map.setAllianceMode(value);
                } catch (Exception e) {
                    //Do nothing
                }
            } else if (Constants.FOW_MODE.equals(identification)) {
                try {
                    boolean value = Boolean.parseBoolean(tokenizer[1]);
                    map.setFoWMode(value);
                } catch (Exception e) {
                    //Do nothing
                }
            } else if (Constants.CREATION_DATE.equals(identification)) {
                map.setCreationDate(tokenizer[1]);
            } else if (Constants.ROUND.equals(identification)) {
                String roundNumber = tokenizer[1];
                try {
                    map.setRound(Integer.parseInt(roundNumber));
                } catch (Exception exception) {
                    BotLogger.log("Could not parse round number");
                }
            } else if (Constants.LAST_MODIFIED_DATE.equals(identification)) {
                String lastModificationDate = tokenizer[1];
                try {
                    map.setLastModifiedDate(Long.parseLong(lastModificationDate));
                } catch (Exception exception) {
                    BotLogger.log("Could not parse last modified date");
                }
            }

        }
    }

    private static ArrayList<String> getCardList(String tokenizer) {
        StringTokenizer cards = new StringTokenizer(tokenizer, ",");
        ArrayList<String> cardList = new ArrayList<>();
        while (cards.hasMoreTokens()) {
            cardList.add(cards.nextToken());
        }
        return cardList;
    }

    private static LinkedHashMap<String, Integer> getParsedCards(String tokenizer) {
        StringTokenizer actionCardToken = new StringTokenizer(tokenizer, ";");
        LinkedHashMap<String, Integer> cards = new LinkedHashMap<>();
        while (actionCardToken.hasMoreTokens()) {
            StringTokenizer cardInfo = new StringTokenizer(actionCardToken.nextToken(), ",");
            String id = cardInfo.nextToken();
            Integer index = Integer.parseInt(cardInfo.nextToken());
            cards.put(id, index);
        }
        return cards;
    }

    private static LinkedHashMap<String, List<String>> getParsedCardsForScoredPO(String tokenizer) {
        StringTokenizer po = new StringTokenizer(tokenizer, ";");
        LinkedHashMap<String, List<String>> scoredPOs = new LinkedHashMap<>();
        while (po.hasMoreTokens()) {
            StringTokenizer poInfo = new StringTokenizer(po.nextToken(), ",");
            String id = poInfo.nextToken();

            if (poInfo.hasMoreTokens()) {
                StringTokenizer userIDs = new StringTokenizer(poInfo.nextToken(), "-");
                List<String> userIDList = new ArrayList<>();
                while (userIDs.hasMoreTokens()) {
                    userIDList.add(userIDs.nextToken());
                }
                scoredPOs.put(id, userIDList);
            }
        }
        return scoredPOs;
    }

    private static void readPlayerInfo(Player player, String data) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        if (tokenizer.countTokens() == 2) {
            data = tokenizer.nextToken();
            switch (data) {
                case Constants.FACTION -> player.setFaction(tokenizer.nextToken());
                case Constants.COLOR -> player.setColor(tokenizer.nextToken());
                case Constants.ROLE_FOR_COMMUNITY -> setRole(player, tokenizer);
                case Constants.CHANNLE_FOR_COMMUNITY -> setChannel(player, tokenizer);
                case Constants.TACTICAL -> player.setTacticalCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.FLEET -> player.setFleetCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.STRATEGY -> player.setStrategicCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.TG -> player.setTg(Integer.parseInt(tokenizer.nextToken()));
                case Constants.COMMODITIES_TOTAL -> player.setCommoditiesTotal(Integer.parseInt(tokenizer.nextToken()));
                case Constants.COMMODITIES -> player.setCommodities(Integer.parseInt(tokenizer.nextToken()));
                case Constants.AC -> {
                    StringTokenizer actionCardToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (actionCardToken.hasMoreTokens()) {
                        StringTokenizer actionCardInfo = new StringTokenizer(actionCardToken.nextToken(), ",");
                        String id = actionCardInfo.nextToken();
                        Integer index = Integer.parseInt(actionCardInfo.nextToken());
                        player.setActionCard(id, index);
                    }
                }
                case Constants.PROMISSORY_NOTES -> {
                    StringTokenizer pnToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    player.clearPromissoryNotes();
                    while (pnToken.hasMoreTokens()) {
                        StringTokenizer pnInfo = new StringTokenizer(pnToken.nextToken(), ",");
                        String id = pnInfo.nextToken();
                        Integer index = Integer.parseInt(pnInfo.nextToken());
                        player.setPromissoryNote(id, index);
                    }
                }
                case Constants.PROMISSORY_NOTES_PLAY_AREA ->
                        player.setPromissoryNotesInPlayArea(getCardList(tokenizer.nextToken()));
                case Constants.PLANETS -> player.setPlanets(getCardList(tokenizer.nextToken()));
                case Constants.PLANETS_EXHAUSTED -> player.setExhaustedPlanets(getCardList(tokenizer.nextToken()));
                case Constants.PLANETS_ABILITY_EXHAUSTED ->
                        player.setExhaustedPlanetsAbilities(getCardList(tokenizer.nextToken()));
                case Constants.TECH -> player.setTechs(getCardList(tokenizer.nextToken()));
                case Constants.TECH_EXHAUSTED -> player.setExhaustedTechs(getCardList(tokenizer.nextToken()));
                case Constants.RELICS -> player.setRelics(getCardList(tokenizer.nextToken()));
                case Constants.EXHAUSTED_RELICS -> player.setExhaustedRelics(getCardList(tokenizer.nextToken()));
                case Constants.MAHACT_CC -> player.setMahactCC(getCardList(tokenizer.nextToken()));
                case Constants.LEADERS -> {
                    StringTokenizer leaderInfos = new StringTokenizer(tokenizer.nextToken(), ";");
                    try {
                        List<Leader> leaderList = new ArrayList<>();
                        while (leaderInfos.hasMoreTokens()) {
                            String[] split = leaderInfos.nextToken().split(",");
                            Leader leader = new Leader(split[0], split[1]);
                            leader.setTgCount(Integer.parseInt(split[2]));
                            leader.setExhausted(Boolean.parseBoolean(split[3]));
                            leader.setLocked(Boolean.parseBoolean(split[4]));
                            if (split.length == 6) {
                                leader.setActive(Boolean.parseBoolean(split[5]));
                            }
                            leaderList.add(leader);
                        }
                        player.setLeaders(leaderList);
                    } catch (Exception e) {
                        BotLogger.log("Could not parse leaders loading map");
                    }
                }
                case Constants.FOW_SYSTEMS -> {
                    StringTokenizer fow_systems = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (fow_systems.hasMoreTokens()) {
                        String[] system = fow_systems.nextToken().split(",");
                        String position = system[0];
                        String tileID = system[1];
                        String label = system[2];
                        player.addFogTile(tileID, position, label);
                    }
                }
                case Constants.SO_SCORED -> {
                    StringTokenizer secrets = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (secrets.hasMoreTokens()) {
                        StringTokenizer secretInfo = new StringTokenizer(secrets.nextToken(), ",");
                        String id = secretInfo.nextToken();
                        Integer index = Integer.parseInt(secretInfo.nextToken());
                        player.setSecretScored(id, index);
                    }
                }
                case Constants.SO -> {
                    StringTokenizer secrets = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (secrets.hasMoreTokens()) {
                        StringTokenizer secretInfo = new StringTokenizer(secrets.nextToken(), ",");
                        String id = secretInfo.nextToken();
                        Integer index = Integer.parseInt(secretInfo.nextToken());
                        player.setSecret(id, index);
                    }
                }

                case Constants.FRAGMENTS -> {
                    StringTokenizer fragments = new StringTokenizer(tokenizer.nextToken(), ",");
                    while (fragments.hasMoreTokens()) {
                        player.addFragment(fragments.nextToken());
                    }
                }

                case Constants.STRATEGY_CARD -> player.setSC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.PASSED -> player.setPassed(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.SEARCH_WARRANT -> player.setSearchWarrant(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.DUMMY -> player.setDummy(Boolean.parseBoolean(tokenizer.nextToken()));
            }
        }
    }

    private static void setChannel(Player player, StringTokenizer tokenizer) {
        String id = tokenizer.nextToken();
        GuildChannel guildChannelById = MapGenerator.jda.getGuildChannelById(id);
        player.setChannelForCommunity(guildChannelById);
    }

    private static void setRole(Player player, StringTokenizer tokenizer) {
        String id = tokenizer.nextToken();
        Role roleById = MapGenerator.jda.getRoleById(id);
        player.setRoleForCommunity(roleById);
    }

    private static Tile readTile(String tileData) {
        StringTokenizer tokenizer = new StringTokenizer(tileData, " ");
        String tileID = tokenizer.nextToken();
        String position = tokenizer.nextToken();
        if (position.equals("mr")) {
            position = "0a";
        }
        return new Tile(tileID, position);
    }

    private static void readUnit(Tile tile, String data, String spaceHolder) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        tile.addUnit(spaceHolder, tokenizer.nextToken(), tokenizer.nextToken());
    }

    private static void readUnitDamage(Tile tile, String data, String spaceHolder) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        tile.addUnitDamage(spaceHolder, tokenizer.nextToken(), tokenizer.nextToken());
    }

    private static void readPlanetTokens(Tile tile, String data, String unitHolderName) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        if (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.startsWith(Constants.COMMAND)) {
                tile.addCC(token);
            } else if (token.startsWith(Constants.CONTROL)) {
                tile.addControl(token, unitHolderName);
            } else {
                tile.addToken(token, unitHolderName);
            }
        }
    }

    private static void readTokens(Tile tile, String data) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
//        tile.setUnit(tokenizer.nextToken(), tokenizer.nextToken());
        //todo implement token read
    }

}
