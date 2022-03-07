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

    public Tile getTile(String tileID) {
        return tileMap.values().stream()
                .filter(tile -> tile.getTileID().equals(tileID))
                .findFirst()
                .orElse(null);
    }
    public Tile getTileByPostion(String position) {
        return tileMap.get(position);
    }

    public boolean isTileDuplicated(String tileID) {
        return tileMap.values().stream()
                .filter(tile -> tile.getTileID().equals(tileID))
                .count() > 1;
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

    public void clearTileMap() {
        this.tileMap.clear();
    }

    public void setTile(Tile tile){
        tileMap.put(tile.getPosition(), tile);
    }
    public void removeTile(String position){
        tileMap.remove(position);
    }
}
