package ti4.map;


import java.util.HashMap;

public class Map {

    private String ownerID;
    private String ownerName;
    private String name;

    //UserID, UserName
    private HashMap<String, String> players = new HashMap<>();
    private MapStatus mapStatus = MapStatus.open;

    //Position, Tile
    private HashMap<String, Tile> tileMap = new HashMap<>();


    public String getOwnerID() {
        return ownerID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
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

    public void addPlayer(String id, String name){
        if (MapStatus.open.equals(mapStatus)) {
            players.put(id, name);
        }
    }

    public HashMap<String, String> getPlayers() {
        return players;
    }

    public void removePlayer(String name){
        if (MapStatus.open.equals(mapStatus)) {
            String keyToDelete = null;
            for (java.util.Map.Entry<String, String> playerEntry : players.entrySet()) {
                if (playerEntry.getValue().equals(name)) {
                    keyToDelete = playerEntry.getKey();
                    break;
                }
            }
            if (keyToDelete != null) {
                players.remove(keyToDelete);
            }
        }
    }

    public void setMapStatus(MapStatus status){
        mapStatus = status;
    }

    public boolean isMapOpen(){
        return mapStatus == MapStatus.open;
    }

    public String getMapStatus(){
        return mapStatus.value;
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
