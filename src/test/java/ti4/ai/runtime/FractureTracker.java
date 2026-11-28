package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.service.map.FractureService;

/**
 * The Fracture in a self-play game (-Dai.selfplay.fracture=true): it enters play at the start of round 2, brought in by a
 * seat drawn from the seed, which places the ingress its breakthrough calls for; from then on every change of hands of its
 * four planets is recorded with the round and the seat.
 */
final class FractureTracker {

    static final String PROPERTY = "ai.selfplay.fracture";
    static final List<String> PLANETS = List.of("cocytus", "styx", "lethe", "phlegethon");
    private static final int ENTERS_IN_ROUND = 2;

    record Change(int round, String planet, String from, String to) {}

    record Summary(
            boolean enabled,
            int enteredRound,
            String spawner,
            List<String> ingress,
            List<Change> changes,
            Map<String, Integer> firstAttackRound) {

        static final Summary OFF = new Summary(false, 0, "", List.of(), List.of(), Map.of());

        boolean attacked(String planet) {
            return firstAttackRound.containsKey(planet);
        }

        boolean tookEvery(String planet) {
            return changes.stream().anyMatch(change -> change.planet().equals(planet));
        }

        boolean entered() {
            return enteredRound > 0;
        }

        String holderAtEnd(String planet) {
            String holder = "";
            for (Change change : changes) {
                if (change.planet().equals(planet)) holder = change.to();
            }
            return holder;
        }

        int styxChanges() {
            return (int) changes.stream()
                    .filter(change -> "styx".equals(change.planet()))
                    .count();
        }
    }

    static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    private final long seed;
    private int enteredRound;
    private String spawner = "";
    private final List<String> ingress = new ArrayList<>();
    private final Map<String, String> holders = new LinkedHashMap<>();
    private final List<Change> changes = new ArrayList<>();
    private final Map<String, Integer> neutralUnits = new LinkedHashMap<>();
    private final Map<String, Integer> firstAttackRound = new LinkedHashMap<>();

    FractureTracker(long seed) {
        this.seed = seed;
    }

    boolean due(Game game) {
        return enabled() && enteredRound == 0 && game.getRound() >= ENTERS_IN_ROUND;
    }

    // A seat drawn from the seed brings the Fracture in the way a successful roll does: the engine places the ingress
    // tokens its breakthrough calls for and asks that seat to choose the rest, so the ingress differs from game to
    // game.
    void enter(Game game) {
        if (!FractureService.spawnFracture(null, game)) return;
        enteredRound = game.getRound();
        Player seat = spawningSeat(game, seed);
        spawner = seat.getFaction();
        FractureService.spawnIngressTokens(null, game, seat, seat.getBreakthroughID());
        readIngress(game);
        PLANETS.forEach(planet -> neutralUnits.put(planet, neutralUnitsOn(game, planet)));
    }

    // SplittableRandom mixes the seed, so the consecutive seeds of a batch spread over the seats; java.util.Random
    // gives nearly the same first draw for neighbouring seeds.
    static Player spawningSeat(Game game, long seed) {
        List<Player> seats = game.getRealPlayers().stream()
                .sorted(java.util.Comparator.comparing(Player::getFaction))
                .toList();
        return seats.get(new java.util.SplittableRandom(seed).nextInt(seats.size()));
    }

    private void readIngress(Game game) {
        ingress.clear();
        game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS))
                .map(Tile::getPosition)
                .sorted()
                .forEach(ingress::add);
    }

    private void startFollowing(Game game) {
        enteredRound = game.getRound();
        readIngress(game);
        PLANETS.forEach(planet -> neutralUnits.put(planet, neutralUnitsOn(game, planet)));
    }

    // Without the flag the Fracture can still enter by itself (an expedition roll, a breakthrough): from then on its
    // planets are followed the same way. The seat that brought it in places its chosen ingress tokens over the next
    // ticks, so the ingress is read again for the rest of that round.
    void observe(Game game) {
        if (enteredRound == 0 && FractureService.isFractureInPlay(game)) startFollowing(game);
        if (enteredRound == 0) return;
        if (game.getRound() == enteredRound) readIngress(game);
        for (String planet : PLANETS) {
            Player holder = game.getPlayerThatControlsPlanet(planet);
            String now = holder == null || holder.isNeutral() ? "" : holder.getFaction();
            if (neutralUnitsOn(game, planet) < neutralUnits.getOrDefault(planet, 0)) {
                firstAttackRound.putIfAbsent(planet, game.getRound());
            }
            String before = holders.getOrDefault(planet, "");
            if (!now.equals(before)) {
                changes.add(new Change(game.getRound(), planet, before, now));
                holders.put(planet, now);
            }
        }
    }

    private static int neutralUnitsOn(Game game, String planet) {
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        Tile tile = game.getTileFromPlanet(planet);
        if (neutral == null || tile == null) return 0;
        return tile.getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitKeysForPlayer(neutral).stream()
                        .mapToInt(holder::getUnitCount)
                        .sum())
                .sum();
    }

    Summary summary() {
        return new Summary(
                enabled(),
                enteredRound,
                spawner,
                List.copyOf(ingress),
                List.copyOf(changes),
                Map.copyOf(firstAttackRound));
    }
}
