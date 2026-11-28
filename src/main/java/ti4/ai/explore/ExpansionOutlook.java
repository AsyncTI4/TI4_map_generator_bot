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
import ti4.ai.eval.MoveBudget;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.ShipSpeed;
import ti4.ai.eval.ValueScale;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

final class ExpansionOutlook {

    private static final int MAX_ACTIVATIONS = 3;
    private static final int EARLY_ROUND = 2;
    private static final double EARLY_CLAIM_FACTOR = 1.5;

    private record Transport(Tile tile, UnitType type) {}

    private record Claim(Tile tile, List<Planet> planets) {
        double value() {
            return planets.stream().mapToDouble(BoardView::planetValue).sum();
        }

        double delayCost(Game game, int infantry) {
            return planets.stream()
                    .skip(Math.max(0, infantry))
                    .mapToDouble(planet -> delayedClaimCost(game, planet))
                    .sum();
        }

        int planetCount() {
            return planets.size();
        }
    }

    static double delayedClaimCost(Game game, Planet planet) {
        double best = planet.getMaxResInf() * ValueScale.RESOURCE;
        return game.getRound() <= EARLY_ROUND ? EARLY_CLAIM_FACTOR * best : best;
    }

    private record Launch(Claim claim, Transport transport, int move) {}

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
            if (spare < launch.claim().planetCount()) {
                return launch.claim().delayCost(game, spare);
            }
            spare -= launch.claim().planetCount();
        }
        return 0;
    }

    private List<Launch> nextLaunches(Tile holdingTile) {
        List<Transport> idle = new ArrayList<>(transports());
        List<Launch> launches = new ArrayList<>();
        int activations = Math.min(MAX_ACTIVATIONS, seat.getTacticalCC());
        boolean tempestaLeft = MoveBudget.tempestaThisAction(game, seat);
        for (Claim claim : claims()) {
            if (launches.size() >= activations) break;
            if (alreadyGarrisoned(claim.tile(), holdingTile)) continue;
            Optional<Launch> launch = launchWith(idle, claim, 0);
            if (launch.isEmpty() && tempestaLeft) {
                launch = launchWith(idle, claim, 1);
                tempestaLeft = launch.isEmpty();
            }
            launch.ifPresent(found -> {
                idle.remove(found.transport());
                launches.add(found);
            });
        }
        return launches;
    }

    private Optional<Launch> launchWith(List<Transport> idle, Claim claim, int tempesta) {
        return idle.stream()
                .map(transport -> new Launch(claim, transport, moveTo(transport, claim.tile(), tempesta)))
                .filter(launch -> canReach(launch.transport(), claim.tile(), launch.move()))
                .findFirst();
    }

    private boolean alreadyGarrisoned(Tile tile, Tile holdingTile) {
        int removed = tile == holdingTile ? 1 : 0;
        return groundForcesIn(tile) - removed > 0;
    }

    private boolean canReach(Transport transport, Tile target, int move) {
        return reach(transport.tile(), move).containsKey(target.getPosition());
    }

    private int moveTo(Transport transport, Tile target, int tempesta) {
        int speed = ShipSpeed.of(game, seat, transport.type(), transport.tile(), target);
        return speed == 0 ? 0 : speed + MoveBudget.everyActionOneShip(seat, target) + tempesta;
    }

    private Set<String> pickupPositions(List<Launch> launches) {
        Set<String> positions = new HashSet<>();
        Tile home = seat.getHomeSystemTile();
        if (home != null && !launches.isEmpty()) positions.add(home.getPosition());
        for (Launch launch : launches) {
            Transport transport = launch.transport();
            int move = launch.move();
            Map<String, Integer> out = reach(transport.tile(), move);
            Map<String, Integer> back = reach(launch.claim().tile(), move);
            out.forEach((position, distance) -> {
                if (back.containsKey(position) && distance + back.get(position) <= move) {
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
        if (move <= 0) return Map.of();
        return reaches.computeIfAbsent(
                origin.getPosition() + "/" + move,
                key -> MovementGraph.distances(game, seat, origin.getPosition(), move));
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
                int move = ShipSpeed.printed(game, seat, type);
                if (type == UnitType.Fighter || move <= 0 || BoardView.capacity(seat, type) <= 0) return;
                for (int ship = 0; ship < count; ship++) found.add(new Transport(tile, type));
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
                            .sorted(Comparator.comparingDouble(BoardView::planetValue)
                                    .thenComparingDouble(planet -> delayedClaimCost(game, planet))
                                    .reversed())
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
