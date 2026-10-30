package ti4.ai.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

final class ExpansionOutlook {

    private static final int MAX_ACTIVATIONS = 3;

    private record Transport(Tile tile, int move) {}

    private record Claim(Tile tile, List<Double> planetValues) {
        double value() {
            return planetValues.stream().mapToDouble(Double::doubleValue).sum();
        }

        double unfundedValue(int infantry) {
            return planetValues.stream()
                    .skip(Math.max(0, infantry))
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }

        int planets() {
            return planetValues.size();
        }
    }

    private record Launch(Claim claim, Transport transport) {}

    private final Game game;
    private final Player seat;
    private final Map<String, Map<String, Integer>> reaches = new HashMap<>();
    private List<Transport> transports;
    private List<Claim> claims;

    ExpansionOutlook(Game game, Player seat) {
        this.game = game;
        this.seat = seat;
    }

    double need(Planet holding) {
        Tile holdingTile = game.getTileFromPlanet(holding.getName());
        List<Launch> launches = nextLaunches(holdingTile);
        Set<String> pool = pickupPositions(launches);
        if (holdingTile == null || !pool.contains(holdingTile.getPosition())) return 0;
        int spare = groundForcesIn(pool) - 1;
        for (Launch launch : launches) {
            if (spare < launch.claim().planets()) {
                return ExploreValues.EXPANSION_SHARE * launch.claim().unfundedValue(spare);
            }
            spare -= launch.claim().planets();
        }
        return 0;
    }

    private List<Launch> nextLaunches(Tile holdingTile) {
        List<Transport> idle = new ArrayList<>(transports());
        List<Launch> launches = new ArrayList<>();
        int activations = Math.min(MAX_ACTIVATIONS, seat.getTacticalCC());
        for (Claim claim : claims()) {
            if (launches.size() >= activations) break;
            if (alreadyGarrisoned(claim.tile(), holdingTile)) continue;
            Optional<Transport> carrier = idle.stream()
                    .filter(transport -> canReach(transport, claim.tile()))
                    .findFirst();
            carrier.ifPresent(transport -> {
                idle.remove(transport);
                launches.add(new Launch(claim, transport));
            });
        }
        return launches;
    }

    private boolean alreadyGarrisoned(Tile tile, Tile holdingTile) {
        int removed = tile == holdingTile ? 1 : 0;
        return groundForcesIn(tile) - removed > 0;
    }

    private boolean canReach(Transport transport, Tile target) {
        return reach(transport.tile(), transport.move()).containsKey(target.getPosition());
    }

    private Set<String> pickupPositions(List<Launch> launches) {
        Set<String> positions = new HashSet<>();
        Tile home = seat.getHomeSystemTile();
        if (home != null && !launches.isEmpty()) positions.add(home.getPosition());
        for (Launch launch : launches) {
            Transport transport = launch.transport();
            Map<String, Integer> out = reach(transport.tile(), transport.move());
            Map<String, Integer> back = reach(launch.claim().tile(), transport.move());
            out.forEach((position, distance) -> {
                if (back.containsKey(position) && distance + back.get(position) <= transport.move()) {
                    positions.add(position);
                }
            });
        }
        return positions;
    }

    private int groundForcesIn(Set<String> positions) {
        return positions.stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null)
                .mapToInt(this::groundForcesIn)
                .sum();
    }

    private int groundForcesIn(Tile tile) {
        int forces = BoardView.groundForces(BoardView.space(tile), seat);
        for (Planet planet : tile.getPlanetUnitHolders()) forces += BoardView.groundForces(planet, seat);
        return forces;
    }

    private Map<String, Integer> reach(Tile origin, int move) {
        return reaches.computeIfAbsent(
                origin.getPosition() + "/" + move, key -> MovementGraph.reach(game, seat, origin.getPosition(), move));
    }

    private List<Transport> transports() {
        if (transports == null) transports = computeTransports();
        return transports;
    }

    private List<Claim> claims() {
        if (claims == null) claims = computeClaims();
        return claims;
    }

    private List<Transport> computeTransports() {
        List<Transport> found = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            BoardView.ships(BoardView.space(tile), seat).forEach((type, count) -> {
                int move = BoardView.moveValueWithGravityDrive(seat, type);
                if (type == UnitType.Fighter || move <= 0 || BoardView.capacity(seat, type) <= 0) return;
                for (int ship = 0; ship < count; ship++) found.add(new Transport(tile, move));
            });
        }
        return found;
    }

    private List<Claim> computeClaims() {
        List<Claim> found = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            List<Planet> open = tile.getPlanetUnitHolders().stream()
                    .filter(this::isClaimable)
                    .toList();
            if (open.isEmpty()) continue;
            found.add(new Claim(
                    tile,
                    open.stream()
                            .map(BoardView::planetValue)
                            .sorted(Comparator.reverseOrder())
                            .toList()));
        }
        found.sort(Comparator.comparingDouble(Claim::value).reversed());
        return found;
    }

    private boolean isClaimable(Planet planet) {
        boolean worthHaving = !planet.getPlanetTypes().isEmpty() || planet.getResources() > 0;
        return worthHaving
                && BoardView.controller(game, planet.getName()) == null
                && !BoardView.hasCustodians(planet)
                && !BoardView.enemyGroundForcesOn(game, seat, planet);
    }
}
