package ti4.helpers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.FOWPlusService;

public final class PlayerNeighbourGraph {

    private static final int MAX_PLAYERS_FOR_MUTUAL_NEIGHBOURS = 30;

    private final Game game;
    private final List<Player> realPlayers;
    private final boolean travelBanInPlay;
    private final Map<Player, List<Tile>> occupiedTiles = new IdentityHashMap<>();
    private final Map<Player, Map<Tile, Boolean>> presence = new IdentityHashMap<>();
    private final Map<String, Set<Player>> playersAdjacentToPosition = new HashMap<>();
    private final Map<Player, Set<Player>> directNeighbours = new IdentityHashMap<>();

    public PlayerNeighbourGraph(Game game) {
        this.game = game;
        realPlayers = game.getRealPlayers();
        travelBanInPlay = ButtonHelper.isLawInPlay(game, "travel_ban");
    }

    public Set<Player> neighboursOf(Player player) {
        Set<Player> neighbours = new HashSet<>(directNeighboursOf(player));
        if (realPlayers.size() < MAX_PLAYERS_FOR_MUTUAL_NEIGHBOURS) {
            for (Player other : realPlayers) {
                if (!neighbours.contains(other) && directNeighboursOf(other).contains(player)) {
                    neighbours.add(other);
                }
            }
        }
        return inRealPlayerOrder(neighbours);
    }

    public Set<Player> directNeighboursOf(Player player) {
        Set<Player> cached = directNeighbours.get(player);
        if (cached != null) return cached;

        Set<Player> neighbours = new HashSet<>();
        for (Tile tile : occupiedTilesOf(player)) {
            if (neighbours.size() == realPlayers.size()) break;
            neighbours.addAll(playersAdjacentTo(tile.getPosition()));
        }
        neighbours.remove(player);
        Set<Player> ordered = inRealPlayerOrder(neighbours);
        directNeighbours.put(player, ordered);
        return ordered;
    }

    private List<Tile> occupiedTilesOf(Player player) {
        return occupiedTiles.computeIfAbsent(
                player,
                p -> game.getTileMap().values().stream()
                        .filter(tile -> isInSystem(p, tile))
                        .toList());
    }

    private Set<Player> playersAdjacentTo(String position) {
        Set<Player> cached = playersAdjacentToPosition.get(position);
        if (cached != null) return cached;

        Set<Player> players = new HashSet<>();
        if (!FOWPlusService.isVoid(game, position)) {
            Set<String> adjacentPositions = FoWHelper.getAdjacentTiles(game, position, null, false);
            for (Player player : realPlayers) {
                if (isInAnyOf(player, adjacentPositions)
                        || isInAnyOf(player, extraWormholeAdjacencies(position, player))) {
                    players.add(player);
                }
            }
        }
        playersAdjacentToPosition.put(position, players);
        return players;
    }

    private Set<String> extraWormholeAdjacencies(String position, Player player) {
        if (!travelBanInPlay && !player.hasAbility("quantum_entanglement")) return Set.of();
        return FoWHelper.getWormholeAdjacencies(game, position, player, true);
    }

    private boolean isInAnyOf(Player player, Set<String> positions) {
        for (String position : positions) {
            Tile tile = game.getTileByPosition(position);
            if (tile != null && isInSystem(player, tile)) return true;
        }
        return false;
    }

    private boolean isInSystem(Player player, Tile tile) {
        return presence.computeIfAbsent(player, p -> new IdentityHashMap<>())
                .computeIfAbsent(tile, t -> FoWHelper.playerIsInSystem(game, t, player, true));
    }

    private Set<Player> inRealPlayerOrder(Set<Player> players) {
        Set<Player> ordered = new LinkedHashSet<>();
        for (Player player : realPlayers) {
            if (players.contains(player)) ordered.add(player);
        }
        return ordered;
    }
}
