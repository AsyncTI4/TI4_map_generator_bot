package ti4.map;


import ti4.generator.Mapper;

import javax.annotation.CheckForNull;
import java.util.*;

public class Map {

    private String ownerID;
    private String ownerName = "";
    private String name;

    //UserID, UserName
    private LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    private MapStatus mapStatus = MapStatus.open;

    private List<String> secretObjectives = new ArrayList<>();

    public Map() {
        //todo temp
        HashMap<String, String> secretObjectives = Mapper.getSecretObjectives();
        this.secretObjectives = new ArrayList<>(secretObjectives.keySet());
        Collections.shuffle(this.secretObjectives);
    }

    //Position, Tile
    private HashMap<String, Tile> tileMap = new HashMap<>();



    @CheckForNull
    public LinkedHashMap<String, Integer> drawSecretObjective(String userID) {
        if (!secretObjectives.isEmpty()) {
            String id = secretObjectives.get(0);
            Player player = getPlayer(userID);
            if (player != null) {
                secretObjectives.remove(id);
                player.setSecret(id);
                return player.getSecrets();
            }
        }
        return null;
    }

    public boolean discardSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)){
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()){
                player.removeSecret(soIDNumber);
                secretObjectives.add(soID);
                Collections.shuffle(secretObjectives);
                return true;
            }
        }
        return false;
    }

    @CheckForNull
    public LinkedHashMap<String, Integer> getSecretObjective(String userID) {
        Player player = getPlayer(userID);
        if (player != null) {
            return player.getSecrets();
        }
        return null;
    }

    @CheckForNull
    public LinkedHashMap<String, Integer> getScoredSecretObjective(String userID) {
        Player player = getPlayer(userID);
        if (player != null) {
            return player.getSecretsScored();
        }
        return null;
    }

    public void addSecretObjective(String id) {
        if (!secretObjectives.contains(id)) {
            secretObjectives.add(id);
            Collections.shuffle(this.secretObjectives);
        }
    }

    public List<String> getSecretObjectives() {
        return secretObjectives;
    }

    public void setSecretObjectives(List<String> secretObjectives) {
        this.secretObjectives = secretObjectives;
    }

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

    public void addPlayer(String id, String name) {
        if (MapStatus.open.equals(mapStatus)) {
            Player player = new Player(id, name);
            players.put(id, player);
        }
    }

    public Player addPlayerLoad(String id, String name) {
        Player player = new Player(id, name);
        players.put(id, player);
        return player;
    }

    public LinkedHashMap<String, Player> getPlayers() {
        return players;
    }

    public void setPlayers(LinkedHashMap<String, Player> players) {
        this.players = players;
    }

    public Player getPlayer(String userID) {
        return players.get(userID);
    }

    public Set<String> getPlayerIDs() {
        return players.keySet();
    }

    public void removePlayer(String playerID) {
        if (MapStatus.open.equals(mapStatus)) {
            players.remove(playerID);
        }
    }

    public void setMapStatus(MapStatus status) {
        mapStatus = status;
    }

    public boolean isMapOpen() {
        return mapStatus == MapStatus.open;
    }

    public String getMapStatus() {
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

    public void setTile(Tile tile) {
        tileMap.put(tile.getPosition(), tile);
    }

    public void removeTile(String position) {
        tileMap.remove(position);
    }
}
