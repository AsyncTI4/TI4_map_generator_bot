package ti4.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Territory;
import ti4.ai.eval.Territory.Zone;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class Hostility {

    public static final String KEY = "aiHostility";
    static final String CARDS_KEY = "aiHostilityCards";
    static final String PRESENCE_KEY = "aiHostilityPresence";
    static final String TARGETS_KEY = "aiHostilityTargets";
    static final double MEAN_CARD_COST = 2.0;
    static final double SABOTAGE_COST = 1.0;
    static final double SLICE_PRESENCE_COST = 1.0;
    static final double HOME_PRESENCE_COST = 3.0;
    static final double BLOCKADE_COST = 2.0;
    static final double HOME_FACTOR = 3.0;
    static final double SLICE_FACTOR = 1.5;
    private static final String SAVE_REASON = "AI hostility";
    private static final String PAIR = ">";
    private static final String AMOUNT = "=";
    private static final String TOTALS = ",";
    private static final String ENTRIES = ";";
    private static final String FIELDS = "/";
    private static final String FORMAT = "%.2f";
    private static final String SABOTAGE = "Sabotage";

    private record Cost(String offender, String victim, double tradeGoods) {}

    private record Presence(String key, Cost cost) {}

    private record CardScan(List<Cost> costs, int processed, int targets) {}

    public static double amount(Game game, Player victim, Player offender) {
        return totals(game).getOrDefault(pair(offender.getFaction(), victim.getFaction()), 0.0);
    }

    public static void add(Game game, String offender, String victim, double tradeGoods) {
        if (tradeGoods <= 0 || offender.equals(victim)) return;
        Map<String, Double> totals = totals(game);
        totals.merge(pair(offender, victim), tradeGoods, Double::sum);
        game.setStoredValue(KEY, encode(totals));
    }

    public static void charge(Game game, Player offender, Player victim, double tradeGoods) {
        if (tradeGoods <= 0) return;
        String offending = offender.getFaction();
        String suffering = victim.getFaction();
        AiGameWrites.persist(game, SAVE_REASON, latest -> add(latest, offending, suffering, tradeGoods));
    }

    public static Optional<AiDecision> observe(AiTurnContext context) {
        record(context.game());
        return Optional.empty();
    }

    static void record(Game game) {
        Set<String> seen = seenPresence(game);
        List<Presence> presence = presence(game).stream()
                .filter(found -> !seen.contains(found.key()))
                .toList();
        int processed = processedCards(game);
        int targets = processed(game, TARGETS_KEY);
        CardScan scan = scanCards(game, processed, targets);
        if (presence.isEmpty() && scan.processed() == processed && scan.targets() == targets) return;
        AiGameWrites.persist(game, SAVE_REASON, latest -> {
            Set<String> marked = seenPresence(latest);
            for (Presence found : presence) {
                if (marked.add(found.key())) apply(latest, found.cost());
            }
            latest.setStoredValue(PRESENCE_KEY, String.join(ENTRIES, marked));
            if (processedCards(latest) < scan.processed() || processed(latest, TARGETS_KEY) < scan.targets()) {
                scan.costs().forEach(cost -> apply(latest, cost));
                latest.setStoredValue(CARDS_KEY, String.valueOf(scan.processed()));
                latest.setStoredValue(TARGETS_KEY, String.valueOf(scan.targets()));
            }
        });
    }

    private static void apply(Game game, Cost cost) {
        add(game, cost.offender(), cost.victim(), cost.tradeGoods());
    }

    private static List<Presence> presence(Game game) {
        List<Presence> found = new ArrayList<>();
        for (Player victim : game.getRealPlayers()) {
            Territory territory = Territory.of(game, victim);
            for (Tile tile : game.getTileMap().values()) {
                Zone zone = territory.zone(tile);
                boolean dock = hasDock(tile, victim);
                if (!dock && !ownGround(zone)) continue;
                boolean blockade = dock && !FoWHelper.playerHasActualShipsInSystem(victim, tile);
                double parked = ownGround(zone) ? parkedCost(zone) : 0;
                double cost = Math.max(parked, blockade ? BLOCKADE_COST : 0);
                if (cost <= 0) continue;
                for (Player offender : game.getRealPlayers()) {
                    if (offender == victim || !FoWHelper.playerHasActualShipsInSystem(offender, tile)) continue;
                    found.add(presence(game, offender, victim, tile, cost));
                }
            }
        }
        return found;
    }

    private static Presence presence(Game game, Player offender, Player victim, Tile tile, double tradeGoods) {
        String key = game.getRound()
                + FIELDS
                + offender.getFaction()
                + FIELDS
                + victim.getFaction()
                + FIELDS
                + tile.getPosition();
        return new Presence(key, new Cost(offender.getFaction(), victim.getFaction(), tradeGoods));
    }

    private static boolean ownGround(Zone zone) {
        return zone == Zone.HOME || zone == Zone.SLICE || zone == Zone.LANE;
    }

    private static double parkedCost(Zone zone) {
        return zone == Zone.HOME ? HOME_PRESENCE_COST : SLICE_PRESENCE_COST;
    }

    public static double location(Game game, Tile tile, Player victim) {
        Zone zone = Territory.of(game, victim).zone(tile);
        if (zone == Zone.HOME) return HOME_FACTOR;
        return ownGround(zone) ? SLICE_FACTOR : 1.0;
    }

    private static boolean hasDock(Tile tile, Player player) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> BoardView.count(planet, player, UnitType.Spacedock) > 0);
    }

    private static CardScan scanCards(Game game, int processed, int targets) {
        List<Cost> found = new ArrayList<>(meanCards(game, targets));
        List<GameStats.ActionCardTarget> aimed = game.getGameStats().getActionCardTargets();
        int scanned = scanSabotage(game, processed, found);
        return new CardScan(found, scanned, aimed.size());
    }

    private static List<Cost> meanCards(Game game, int targets) {
        List<GameStats.ActionCardTarget> aimed = game.getGameStats().getActionCardTargets();
        List<Cost> found = new ArrayList<>();
        for (int index = Math.min(targets, aimed.size()); index < aimed.size(); index++) {
            Player player = byTrackedId(game, aimed.get(index).getPlayerId());
            Player target = byTrackedId(game, aimed.get(index).getTargetId());
            if (player != null && target != null && player != target) {
                found.add(new Cost(player.getFaction(), target.getFaction(), MEAN_CARD_COST));
            }
        }
        return found;
    }

    private static int scanSabotage(Game game, int processed, List<Cost> found) {
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        int index = Math.min(processed, plays.size());
        for (; index < plays.size(); index++) {
            GameStats.ActionCardPlay play = plays.get(index);
            Player player = byTrackedId(game, play.getPlayerId());
            if (player == null) continue;
            if (!SABOTAGE.equals(play.getActionCard())) continue;
            Optional<Player> victim = sabotaged(game, plays, index, player);
            if (victim.isEmpty() && index == plays.size() - 1) break;
            victim.ifPresent(target -> found.add(new Cost(player.getFaction(), target.getFaction(), SABOTAGE_COST)));
        }
        return index;
    }

    private static Optional<Player> sabotaged(
            Game game, List<GameStats.ActionCardPlay> plays, int sabotage, Player saboteur) {
        for (int index = sabotage - 1; index >= 0; index--) {
            GameStats.ActionCardPlay play = plays.get(index);
            if (!play.isCanceled()) continue;
            Player target = byTrackedId(game, play.getPlayerId());
            if (target != null && target != saboteur) return Optional.of(target);
        }
        return Optional.empty();
    }

    private static Player byTrackedId(Game game, String playerId) {
        if (playerId == null) return null;
        return game.getRealPlayers().stream()
                .filter(player -> playerId.equals(GameStats.getTrackedPlayerId(player)))
                .findFirst()
                .orElse(null);
    }

    private static int processedCards(Game game) {
        return processed(game, CARDS_KEY);
    }

    private static int processed(Game game, String key) {
        String stored = game.getStoredValue(key);
        return NumberUtils.isCreatable(stored) ? Integer.parseInt(stored) : 0;
    }

    private static Set<String> seenPresence(Game game) {
        String round = game.getRound() + FIELDS;
        return Arrays.stream(StringUtils.split(game.getStoredValue(PRESENCE_KEY), ENTRIES))
                .filter(entry -> entry.startsWith(round))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String pair(String offender, String victim) {
        return offender + PAIR + victim;
    }

    private static Map<String, Double> totals(Game game) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (String entry : StringUtils.split(game.getStoredValue(KEY), TOTALS)) {
            String value = StringUtils.substringAfter(entry, AMOUNT);
            if (NumberUtils.isCreatable(value)) {
                totals.put(StringUtils.substringBefore(entry, AMOUNT), Double.parseDouble(value));
            }
        }
        return totals;
    }

    private static String encode(Map<String, Double> totals) {
        return totals.entrySet().stream()
                .map(entry -> entry.getKey() + AMOUNT + String.format(Locale.ROOT, FORMAT, entry.getValue()))
                .collect(Collectors.joining(TOTALS));
    }
}
