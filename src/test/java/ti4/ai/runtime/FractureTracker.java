package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.map.FractureService;

/**
 * The Fracture in a self-play game (-Dai.selfplay.fracture=true): it enters play at the start of round 2 with an ingress
 * token on one system of each technology specialty and on Mecatol Rex, and from then on every change of hands of its
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
            List<String> ingress,
            List<Change> changes,
            Map<String, Integer> firstAttackRound) {

        static final Summary OFF = new Summary(false, 0, List.of(), List.of(), Map.of());

        boolean attacked(String planet) {
            return firstAttackRound.containsKey(planet);
        }

        boolean tookEvery(String planet) {
            return changes.stream().anyMatch(change -> change.planet().equals(planet));
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

    void enter(Game game) {
        FractureService.spawnFracture(null, game);
        enteredRound = game.getRound();
        Random random = new Random(seed);
        List<Tile> chosen = new ArrayList<>();
        for (TechnologyType type : TechnologyType.mainFour) {
            List<Tile> candidates = game.getTileMap().values().stream()
                    .filter(tile -> !chosen.contains(tile))
                    .filter(tile ->
                            tile.getPlanetUnitHolders().stream().anyMatch(planet -> planet.hasTechSpecialty(type)))
                    .sorted(java.util.Comparator.comparing(Tile::getPosition))
                    .toList();
            if (!candidates.isEmpty()) chosen.add(candidates.get(random.nextInt(candidates.size())));
        }
        Tile mecatol = game.getMecatolTile();
        if (mecatol != null) chosen.add(mecatol);
        for (Tile tile : chosen) {
            tile.addToken(Constants.TOKEN_INGRESS, "space");
            ingress.add(tile.getPosition());
        }
        Collections.sort(ingress);
        PLANETS.forEach(planet -> neutralUnits.put(planet, neutralUnitsOn(game, planet)));
    }

    void observe(Game game) {
        if (enteredRound == 0) return;
        for (String planet : PLANETS) {
            Player holder = game.getPlayerThatControlsPlanet(planet);
            String now = holder == null ? "" : holder.getFaction();
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
                enabled(), enteredRound, List.copyOf(ingress), List.copyOf(changes), Map.copyOf(firstAttackRound));
    }
}
