package ti4.map;

import ti4.ResourceHelper;

import java.util.HashMap;

public class MapManager {

    private static MapManager mapManager = null;

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
}
