package ti4.map;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Map {

    private String ownerID;
    private String ownerName = "";
    private String name;

    //UserID, UserName
    private HashMap<String, Player> players = new HashMap<>();
    private MapStatus mapStatus = MapStatus.open;

    //Position, Tile
    private HashMap<String, Tile> tileMap = new HashMap<>();


    public String getOwnerID() {
        return ownerID;
    }

    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
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
            Player player = new Player(id, name);
            players.put(id, player);
        }
    }

    public Player addPlayerLoad(String id, String name){
        Player player = new Player(id, name);
        players.put(id, player);
        return player;
    }

    public HashMap<String, Player> getPlayers() {
        return players;
    }

    public Player getPlayer(String userID) {
        return players.get(userID);
    }
    public Set<String> getPlayerIDs() {
        return players.keySet();
    }

    public void removePlayer(String playerID){
        if (MapStatus.open.equals(mapStatus)) {
            players.remove(playerID);
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
