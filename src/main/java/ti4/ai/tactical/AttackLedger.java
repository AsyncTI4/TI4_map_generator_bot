package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.ValueScale;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.model.UnitModel;

@UtilityClass
public class AttackLedger {

    static final double DECAY_PER_ROUND = 0.5;
    static final int REMEMBERED_ROUNDS = 3;
    private static final String KEY_PREFIX = "aiAttack";
    static final String LEDGER_KEY = KEY_PREFIX + "Ledger";
    static final String UNITS_KEY = KEY_PREFIX + "Units";
    static final String PLANETS_KEY = KEY_PREFIX + "Planets";
    private static final String ENTRIES = ";";
    private static final String FIELDS = "/";
    private static final String VALUE = "=";
    private static final String NONE = "-";
    private static final String FORMAT = "%.2f";

    record Attack(int round, String attacker, String victim, double damage) {

        String key() {
            return round + FIELDS + attacker + FIELDS + victim;
        }
    }

    private record Board(Map<String, Map<String, Double>> units, Map<String, String> planets) {}

    public static Optional<AiDecision> observe(AiTurnContext context) {
        if (AiExperiments.enabled(context.seat(), Experiment.ATTACK_MEMORY)) record(context.game());
        return Optional.empty();
    }

    public static boolean isLedgerKey(String key) {
        return key.startsWith(KEY_PREFIX);
    }

    public static boolean remembers(Game game, Player seat) {
        return AiExperiments.enabled(DecidingSeat.orSelf(game, seat), Experiment.ATTACK_MEMORY);
    }

    public static double grudge(Game game, Player holder, Player attacker) {
        if (holder == null || attacker == null || holder == attacker) return 0;
        double total = 0;
        for (Attack attack : attacks(game)) {
            if (attack.attacker().equals(attacker.getFaction())
                    && attack.victim().equals(holder.getFaction())) {
                total += attack.damage() * decay(game.getRound() - attack.round());
            }
        }
        return total;
    }

    static double decay(int roundsAgo) {
        if (roundsAgo >= REMEMBERED_ROUNDS) return 0;
        return Math.pow(DECAY_PER_ROUND, Math.max(0, roundsAgo));
    }

    static void record(Game game) {
        Board now = board(game);
        boolean seen = !game.getStoredValue(UNITS_KEY).isEmpty();
        Map<String, Attack> ledger = new LinkedHashMap<>();
        for (Attack attack : attacks(game)) {
            if (decay(game.getRound() - attack.round()) > 0) ledger.put(attack.key(), attack);
        }
        if (seen)
            diff(game, readBoard(game), now).forEach(attack -> ledger.merge(attack.key(), attack, AttackLedger::sum));
        game.setStoredValue(LEDGER_KEY, encodeLedger(ledger.values()));
        game.setStoredValue(UNITS_KEY, encodeUnits(now.units()));
        game.setStoredValue(PLANETS_KEY, encodePlanets(now.planets()));
    }

    static List<Attack> attacks(Game game) {
        List<Attack> attacks = new ArrayList<>();
        for (String entry : StringUtils.split(game.getStoredValue(LEDGER_KEY), ENTRIES)) {
            String[] fields = StringUtils.split(StringUtils.substringBefore(entry, VALUE), FIELDS);
            String damage = StringUtils.substringAfter(entry, VALUE);
            if (fields.length != 3 || !NumberUtils.isCreatable(fields[0]) || !NumberUtils.isCreatable(damage)) continue;
            attacks.add(new Attack(Integer.parseInt(fields[0]), fields[1], fields[2], Double.parseDouble(damage)));
        }
        return attacks;
    }

    private static Attack sum(Attack first, Attack second) {
        return new Attack(first.round(), first.attacker(), first.victim(), first.damage() + second.damage());
    }

    private static List<Attack> diff(Game game, Board before, Board after) {
        List<Attack> found = new ArrayList<>();
        Player active = game.getActivePlayer();
        for (Player victim : game.getRealPlayers()) {
            found.addAll(unitLosses(game, victim, active, before, after));
        }
        before.planets().forEach((planet, owner) -> {
            String taker = after.planets().get(planet);
            if (taker == null || taker.equals(owner)) return;
            Player victim = game.getPlayerFromColorOrFaction(owner);
            Player attacker = game.getPlayerFromColorOrFaction(taker);
            Tile tile = game.getTileFromPlanet(planet);
            if (victim == null || attacker == null || tile == null) return;
            if (!(tile.getUnitHolders().get(planet) instanceof Planet held)) return;
            double damage = BoardView.planetValue(held) * Grievance.location(game, tile, victim);
            found.add(new Attack(game.getRound(), attacker.getFaction(), victim.getFaction(), damage));
        });
        return found;
    }

    private static List<Attack> unitLosses(
            Game game, Player victim, @Nullable Player active, Board before, Board after) {
        if (victim == active) return List.of();
        String faction = victim.getFaction();
        double died = Math.max(0, total(before, faction) - total(after, faction));
        if (died <= 0) return List.of();
        Map<String, Double> drops = new HashMap<>();
        before.units().forEach((position, costs) -> {
            double drop = costs.getOrDefault(faction, 0.0) - unitsAt(after, position, faction);
            if (drop > 0) drops.put(position, drop);
        });
        double dropped =
                drops.values().stream().mapToDouble(Double::doubleValue).sum();
        double share = Math.min(1, died / dropped);
        List<Attack> found = new ArrayList<>();
        drops.forEach((position, drop) -> {
            Tile tile = game.getTileByPosition(position);
            Player attacker = attackerAt(game, position, victim, active, before, after);
            if (tile == null || attacker == null) return;
            double damage = drop * share * ValueScale.RESOURCE * Grievance.location(game, tile, victim);
            found.add(new Attack(game.getRound(), attacker.getFaction(), faction, damage));
        });
        return found;
    }

    @Nullable
    private static Player attackerAt(
            Game game, String position, Player victim, @Nullable Player active, Board before, Board after) {
        List<Player> present = game.getRealPlayers().stream()
                .filter(player -> player != victim)
                .filter(player -> unitsAt(before, position, player.getFaction()) > 0
                        || unitsAt(after, position, player.getFaction()) > 0)
                .toList();
        if (active != null && present.contains(active)) return active;
        return present.size() == 1 ? present.getFirst() : null;
    }

    private static double unitsAt(Board board, String position, String faction) {
        return board.units().getOrDefault(position, Map.of()).getOrDefault(faction, 0.0);
    }

    private static double total(Board board, String faction) {
        return board.units().values().stream()
                .mapToDouble(costs -> costs.getOrDefault(faction, 0.0))
                .sum();
    }

    private static Board board(Game game) {
        Map<String, Map<String, Double>> units = new TreeMap<>();
        Map<String, String> planets = new TreeMap<>();
        for (Player player : game.getRealPlayers()) {
            for (String planet : player.getPlanets()) planets.put(planet, player.getFaction());
        }
        for (Tile tile : game.getTileMap().values()) {
            for (Player player : game.getRealPlayers()) {
                double cost = unitCost(tile, player);
                if (cost > 0)
                    units.computeIfAbsent(tile.getPosition(), position -> new TreeMap<>())
                            .put(player.getFaction(), cost);
            }
        }
        return new Board(units, planets);
    }

    private static double unitCost(Tile tile, Player player) {
        double cost = 0;
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                UnitModel model = player.getUnitFromUnitKey(key);
                if (model != null) cost += model.getCost() * holder.getUnitCount(key);
            }
        }
        return cost;
    }

    private static Board readBoard(Game game) {
        Map<String, Map<String, Double>> units = new TreeMap<>();
        for (String entry : StringUtils.split(game.getStoredValue(UNITS_KEY), ENTRIES)) {
            String[] fields = StringUtils.split(StringUtils.substringBefore(entry, VALUE), FIELDS);
            String cost = StringUtils.substringAfter(entry, VALUE);
            if (fields.length != 2 || !NumberUtils.isCreatable(cost)) continue;
            units.computeIfAbsent(fields[0], position -> new TreeMap<>()).put(fields[1], Double.parseDouble(cost));
        }
        Map<String, String> planets = new TreeMap<>();
        for (String entry : StringUtils.split(game.getStoredValue(PLANETS_KEY), ENTRIES)) {
            String planet = StringUtils.substringBefore(entry, VALUE);
            String owner = StringUtils.substringAfter(entry, VALUE);
            if (!planet.isEmpty() && !owner.isEmpty()) planets.put(planet, owner);
        }
        return new Board(units, planets);
    }

    private static String encodeLedger(Iterable<Attack> attacks) {
        List<String> entries = new ArrayList<>();
        for (Attack attack : attacks) {
            entries.add(attack.key() + VALUE + String.format(Locale.ROOT, FORMAT, attack.damage()));
        }
        return entries.isEmpty() ? NONE : String.join(ENTRIES, entries);
    }

    private static String encodeUnits(Map<String, Map<String, Double>> units) {
        List<String> entries = new ArrayList<>();
        units.forEach((position, costs) -> costs.forEach((faction, cost) ->
                entries.add(position + FIELDS + faction + VALUE + String.format(Locale.ROOT, FORMAT, cost))));
        return entries.isEmpty() ? NONE : String.join(ENTRIES, entries);
    }

    private static String encodePlanets(Map<String, String> planets) {
        List<String> entries = new ArrayList<>();
        planets.forEach((planet, owner) -> entries.add(planet + VALUE + owner));
        return entries.isEmpty() ? NONE : String.join(ENTRIES, entries);
    }
}
