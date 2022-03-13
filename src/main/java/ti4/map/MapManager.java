package ti4.map;

import java.util.HashMap;

public class MapManager {

    private static MapManager mapManager = null;
    private static HashMap<String, String> setMapForUser = new HashMap<>();

    private MapManager() {
    }

    private HashMap<String, Map> mapList = new HashMap<>();

    public static MapManager getInstance() {
        if (mapManager == null) {
            mapManager = new MapManager();
        }
        return mapManager;
    }


    public HashMap<String, Map> getMapList() {
        return mapList;
    }

    public void setMapList(HashMap<String, Map> mapList) {
        this.mapList = mapList;
    }

    public void addMap(Map map) {
        mapList.put(map.getName(), map);
    }

    public Map getMap(String mapName) {
        return mapList.get(mapName);
    }

    public Map deleteMap(String mapName) {
        return mapList.remove(mapName);
    }

    public boolean setMapForUser(String userID, String mapName) {
        if (mapList.get(mapName) != null) {
            setMapForUser.put(userID, mapName);
            return true;
        }
        return false;
    }

    public void resetMapForUser(String userID) {
        setMapForUser.remove(userID);
    }

    public boolean isUserWithActiveMap(String userID) {
        return setMapForUser.containsKey(userID);
    }

    public Map getUserActiveMap(String userID) {
        String mapName = setMapForUser.get(userID);
        return mapList.get(mapName);
    }


}
