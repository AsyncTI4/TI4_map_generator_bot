package ti4.ai.expedition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FactionAggression;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.Threats;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

final class SiteScore {

    static final int REACH = 4;
    static final int HOME_RADIUS = 2;
    static final double CLOSENESS = 3.0;
    static final double PRESENCE = 1.0;
    static final double FLEET_SCALE = 12;
    static final double THUNDERS_EDGE_CLOSENESS = 1.5;
    static final double THUNDERS_EDGE_PRESSURE = 1.5;
    static final double MARGIN_WEIGHT = 0.5;
    static final int MARGIN_CAP = 2;

    private final Game game;
    private final Player seat;
    private final Map<String, Integer> distances = new HashMap<>();
    private final Map<Player, Map<String, Integer>> rivalHomes = new HashMap<>();

    private SiteScore(Game game, Player seat) {
        this.game = game;
        this.seat = seat;
        anchors().forEach(this::spread);
        game.getRealPlayers().stream()
                .filter(rival -> rival != seat)
                .forEach(rival -> rivalHomes.put(rival, homeReach(rival)));
    }

    static SiteScore of(Game game, Player seat) {
        return new SiteScore(game, seat);
    }

    double ingress(Tile tile) {
        return closeness(tile) + presence(tile) - pressure(tile);
    }

    double thundersEdge(Tile tile) {
        return THUNDERS_EDGE_CLOSENESS * closeness(tile) - THUNDERS_EDGE_PRESSURE * pressure(tile) + margin(tile);
    }

    double closeness(Tile tile) {
        int distance = Math.min(REACH, distances.getOrDefault(tile.getPosition(), REACH));
        return CLOSENESS * (1.0 - (double) distance / REACH);
    }

    private double presence(Tile tile) {
        boolean holdsPlanet = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> seat.getPlanets().contains(planet.getName()));
        return holdsPlanet || BoardView.hasOwnShips(seat, tile) ? PRESENCE : 0;
    }

    private double pressure(Tile tile) {
        double total = 0;
        for (Map.Entry<Player, Map<String, Integer>> rival : rivalHomes.entrySet()) {
            double factor = FactionAggression.of(rival.getKey()).factor();
            double fleet = Math.min(1.0, Threats.incomingFleetCost(game, rival.getKey(), tile) / FLEET_SCALE);
            boolean nearHome = rival.getValue().getOrDefault(tile.getPosition(), REACH + 1) <= HOME_RADIUS;
            total += factor * (fleet + (nearHome ? 1 : 0));
        }
        return total;
    }

    private double margin(Tile tile) {
        int own = distances.getOrDefault(tile.getPosition(), REACH + 1);
        int rival = rivalHomes.values().stream()
                .mapToInt(reach -> reach.getOrDefault(tile.getPosition(), REACH + 1))
                .min()
                .orElse(REACH + 1);
        return MARGIN_WEIGHT * Math.clamp(rival - own, -MARGIN_CAP, MARGIN_CAP);
    }

    private Iterable<String> anchors() {
        Map<String, Boolean> positions = new HashMap<>();
        Optional.ofNullable(seat.getHomeSystemTile()).ifPresent(home -> positions.put(home.getPosition(), true));
        for (String planet : seat.getPlanets()) {
            Optional.ofNullable(game.getTileFromPlanet(planet))
                    .ifPresent(tile -> positions.put(tile.getPosition(), true));
        }
        game.getTileMap().values().stream()
                .filter(tile -> BoardView.hasOwnShips(seat, tile))
                .forEach(tile -> positions.put(tile.getPosition(), true));
        return positions.keySet();
    }

    private void spread(String origin) {
        distances.merge(origin, 0, Math::min);
        MovementGraph.reach(game, seat, origin, REACH)
                .forEach((position, distance) -> distances.merge(position, distance, Math::min));
    }

    private Map<String, Integer> homeReach(Player rival) {
        Tile home = rival.getHomeSystemTile();
        if (home == null) return Map.of();
        Map<String, Integer> reach = new HashMap<>(MovementGraph.reach(game, rival, home.getPosition(), REACH));
        reach.putIfAbsent(home.getPosition(), 0);
        return reach;
    }
}
