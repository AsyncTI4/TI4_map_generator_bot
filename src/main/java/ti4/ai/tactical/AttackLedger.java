package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiGameWrites;
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
    private static final String SAVE_REASON = "AI attack ledger";
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

    private record Picture(Board board, String ledger) {}

    private record GameKey(String name, long created) {

        static GameKey of(Game game) {
            return new GameKey(game.getName(), game.getCreationDateTime());
        }
    }

    private static final Map<GameKey, Picture> PICTURES = new ConcurrentHashMap<>();

    public static Optional<AiDecision> observe(AiTurnContext context) {
        if (AiExperiments.enabled(context.seat(), Experiment.ATTACK_MEMORY)) record(context.game());
        return Optional.empty();
    }

    public static void forget(String gameName) {
        PICTURES.keySet().removeIf(key -> key.name().equals(gameName));
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
        String stored = storedLedger(game);
        GameKey key = GameKey.of(game);
        Picture last = PICTURES.get(key);
        Map<String, Attack> ledger = new LinkedHashMap<>();
        for (Attack attack : attacks(game)) {
            if (decay(game.getRound() - attack.round()) > 0) ledger.put(attack.key(), attack);
        }
        if (last != null && last.ledger().equals(stored)) {
            diff(game, last.board(), now).forEach(attack -> ledger.merge(attack.key(), attack, AttackLedger::sum));
        }
        String updated = encodeLedger(ledger.values());
        PICTURES.put(key, new Picture(now, updated));
        if (!updated.equals(stored)) {
            AiGameWrites.persist(game, SAVE_REASON, latest -> latest.setStoredValue(LEDGER_KEY, updated));
        }
    }

    private static String storedLedger(Game game) {
        String stored = game.getStoredValue(LEDGER_KEY);
        return stored.isEmpty() ? NONE : stored;
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

    // TODO: losses outside combat (agendas, fleet supply, off-turn Direct Hit/Plague) and planets changing hands
    // without an invasion count as attacks; production in the same window hides losses (died is board-wide net), and an
    // active player's combat losses can be charged to a player sharing a tile it moved out of.
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
        String faction = victim.getFaction();
        Map<String, Double> drops = new HashMap<>();
        double ownLosses = 0;
        for (Map.Entry<String, Map<String, Double>> entry : before.units().entrySet()) {
            String position = entry.getKey();
            double drop = entry.getValue().getOrDefault(faction, 0.0) - unitsAt(after, position, faction);
            if (drop <= 0) continue;
            if (ownAttack(game, victim, active, position)) ownLosses += drop;
            else drops.put(position, drop);
        }
        double died = total(before, faction) - total(after, faction) - ownLosses;
        if (died <= 0) return List.of();
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

    private static boolean ownAttack(Game game, Player victim, @Nullable Player active, String position) {
        return victim == active && position.equals(game.getActiveSystem());
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

    private static String encodeLedger(Iterable<Attack> attacks) {
        List<String> entries = new ArrayList<>();
        for (Attack attack : attacks) {
            entries.add(attack.key() + VALUE + String.format(Locale.ROOT, FORMAT, attack.damage()));
        }
        return entries.isEmpty() ? NONE : String.join(ENTRIES, entries);
    }
}
