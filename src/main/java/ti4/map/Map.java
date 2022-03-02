package ti4.map;


import java.util.HashMap;

public class Map {

    private String ownerID;
    private String name;
    private HashMap<String, Tile> tileMap = new HashMap<>();


    public String getOwnerID() {
        return ownerID;
    }

    public String getName() {
        return name;
    }

    public HashMap<String, Tile> getTileMap() {
        return tileMap;
    }
}
