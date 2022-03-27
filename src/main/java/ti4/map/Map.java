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

    private HashMap<Integer, Boolean> scPlayed = new HashMap<>();
    private String speaker = "";

    private List<String> secretObjectives;
    private List<String> actionCards;
    private LinkedHashMap<String, Integer> discardActionCards = new LinkedHashMap<>();

    public Map() {
        HashMap<String, String> secretObjectives = Mapper.getSecretObjectives();
        this.secretObjectives = new ArrayList<>(secretObjectives.keySet());
        Collections.shuffle(this.secretObjectives);

        HashMap<String, String> actionCards = Mapper.getActionCards();
        this.actionCards = new ArrayList<>(actionCards.keySet());
        Collections.shuffle(this.actionCards);
    }

    //Position, Tile
    private HashMap<String, Tile> tileMap = new HashMap<>();

    public HashMap<Integer, Boolean> getScPlayed() {
        return scPlayed;
    }

    public void setSCPlayed(Integer scNumber, Boolean playedStatus) {
        this.scPlayed.put(scNumber, playedStatus);
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    @CheckForNull
    public LinkedHashMap<String, Integer> drawActionCard(String userID) {
        if (!actionCards.isEmpty()) {
            String id = actionCards.get(0);
            Player player = getPlayer(userID);
            if (player != null) {
                actionCards.remove(id);
                player.setActionCard(id);
                return player.getActionCards();
            }
        } else {
            actionCards.addAll(discardActionCards.keySet());
            discardActionCards.clear();
            Collections.shuffle(actionCards);
            return drawActionCard(userID);
        }
        return null;
    }

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

    private void setDiscardActionCard(String id) {
        Collection<Integer> values = discardActionCards.values();
        int identifier = new Random().nextInt(1000);
        while (values.contains(identifier)) {
            identifier = new Random().nextInt(1000);
        }
        discardActionCards.put(id, identifier);
    }

    public boolean discardActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> actionCards = player.getActionCards();
            String acID = "";
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                player.removeActionCard(acIDNumber);
                setDiscardActionCard(acID);
                return true;
            }
        }
        return false;
    }

    public void shuffleActionCards() {
        Collections.shuffle(actionCards);
    }

    public LinkedHashMap<String, Integer> getDiscardActionCards() {
        return discardActionCards;
    }

    public boolean pickActionCard(String userID, Integer acIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            String acID = "";
            for (java.util.Map.Entry<String, Integer> ac : discardActionCards.entrySet()) {
                if (ac.getValue().equals(acIDNumber)) {
                    acID = ac.getKey();
                    break;
                }
            }
            if (!acID.isEmpty()) {
                discardActionCards.remove(acID);
                player.setActionCard(acID);
                return true;
            }
        }
        return false;
    }

    public boolean scoreSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecret(soIDNumber);
                player.setSecretScored(soID);
                return true;
            }
        }
        return false;
    }

    public boolean unscoreSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> secrets = player.getSecretsScored();
            String soID = "";
            for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
                player.removeSecretScored(soIDNumber);
                player.setSecret(soID);
                return true;
            }
        }
        return false;
    }

    public boolean discardSecretObjective(String userID, Integer soIDNumber) {
        Player player = getPlayer(userID);
        if (player != null) {
            LinkedHashMap<String, Integer> secrets = player.getSecrets();
            String soID = "";
            for (java.util.Map.Entry<String, Integer> so : secrets.entrySet()) {
                if (so.getValue().equals(soIDNumber)) {
                    soID = so.getKey();
                    break;
                }
            }
            if (!soID.isEmpty()) {
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
    public LinkedHashMap<String, Integer> getActionCards(String userID) {
        Player player = getPlayer(userID);
        if (player != null) {
            return player.getActionCards();
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

    public List<String> getActionCards() {
        return actionCards;
    }

    public void setSecretObjectives(List<String> secretObjectives) {
        this.secretObjectives = secretObjectives;
    }

    public void setActionCards(List<String> actionCards) {
        this.actionCards = actionCards;
    }

    public void setDiscardActionCards(LinkedHashMap<String, Integer> discardActionCards) {
        this.discardActionCards = discardActionCards;
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
