package ti4.map;


import java.util.HashMap;

public class Map {

    private String ownerID;
    private String name;

    //Position, Tile
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

    public void setOwnerID(String ownerID) {
        this.ownerID = ownerID;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTileMap(HashMap<String, Tile> tileMap) {
        this.tileMap = tileMap;
    }

    public void setTile(Tile tile)
    {
        tileMap.put(tile.getPosition(), tile);
    }
}
