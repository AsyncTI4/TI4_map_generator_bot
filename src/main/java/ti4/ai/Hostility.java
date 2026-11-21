package ti4.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.agenda.AssassinationCost;
import ti4.ai.agenda.PromisedVotes;
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
    static final String PENDING_KEY = "aiHostilityPending";
    static final double MIN_IMPACT = 0.1;
    static final String ASSASSINATE = "Assassinate Representative";
    static final String SPY = "Spy";
    static final double MIN_ASSASSINATION_LOSS = 1.0;
    static final Map<String, Double> FLAT_IMPACT = Map.of("Ancient Burial Sites", 0.5);
    static final Map<String, Double> IMPACT_SHARE = Map.of("Reparations", 0.5);
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
    // Mean cards cost what they actually took from the target, not a flat price: a Tactical Bombardment played as a
    // blank stall, on a system where the target has nothing readied, costs nothing.
    static final String TACTICAL_BOMBARDMENT = "Tactical Bombardment";
    static final String ROUND_KEY = "aiHostilityRound";
    static final double ANGERED = 1.0;
    static final double SPREAD_STEP = 0.25;
    private static final Set<String> NOT_MEAN_TARGETS = Set.of(
            SABOTAGE,
            "Forward Supply Base",
            "Brilliance",
            "Exchange Program",
            "Direct Hit",
            "Courageous to the End",
            "Intercept",
            "Rout",
            "Disable",
            "Reflective Shielding",
            "Waylay",
            "Scramble Frequency",
            "Experimental Battlestation",
            "Parley",
            "Salvage",
            "Infiltrate");

    private record Cost(String offender, String victim, double tradeGoods) {}

    private record Presence(String key, Cost cost) {}

    private record CardScan(List<Cost> costs, List<Pending> pending, int processed, int targets) {}

    enum Kind {
        ASSETS,
        SPY,
        ASSASSINATION
    }

    record Pending(
            String offender,
            String victim,
            double before,
            int plays,
            String active,
            int round,
            Kind kind,
            String detail) {

        Pending(String offender, String victim, double before, int plays, String active, int round) {
            this(offender, victim, before, plays, active, round, Kind.ASSETS, "");
        }

        String encode() {
            return String.join(
                    FIELDS,
                    offender,
                    victim,
                    String.format(Locale.ROOT, FORMAT, before),
                    String.valueOf(plays),
                    active,
                    String.valueOf(round),
                    kind.name(),
                    detail);
        }

        static Optional<Pending> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELDS);
            if (fields == null || (fields.length != 6 && fields.length != 8)) return Optional.empty();
            if (!NumberUtils.isCreatable(fields[2]) || !NumberUtils.isCreatable(fields[3])) return Optional.empty();
            if (!NumberUtils.isCreatable(fields[5])) return Optional.empty();
            Kind kind = fields.length == 8 ? Kind.valueOf(fields[6]) : Kind.ASSETS;
            return Optional.of(new Pending(
                    fields[0],
                    fields[1],
                    Double.parseDouble(fields[2]),
                    Integer.parseInt(fields[3]),
                    fields[4],
                    Integer.parseInt(fields[5]),
                    kind,
                    fields.length == 8 ? fields[7] : ""));
        }

        boolean windowClosed(Game game) {
            return game.getRound() != round
                    || game.getGameStats().getActionCardPlays().size() > plays
                    || !active.equals(StringUtils.defaultString(game.getActivePlayerID()));
        }
    }

    private static final Map<String, Map<String, Double>> LAST_ASSETS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Set<String>>> LAST_HANDS = new ConcurrentHashMap<>();
    private static final String HAND = "+";

    public static double amount(Game game, Player victim, Player offender) {
        return totals(game).getOrDefault(pair(offender.getFaction(), victim.getFaction()), 0.0);
    }

    public static void add(Game game, String offender, String victim, double tradeGoods) {
        if (tradeGoods <= 0 || offender.equals(victim)) return;
        Map<String, Double> totals = totals(game);
        totals.merge(pair(offender, victim), tradeGoods, Double::sum);
        game.setStoredValue(KEY, encode(totals));
        Map<String, Double> round = thisRound(game);
        round.merge(pair(offender, victim), tradeGoods, Double::sum);
        game.setStoredValue(ROUND_KEY, game.getRound() + FIELDS + encode(round));
    }

    public static long angeredThisRound(Game game, Player offender, Player besides) {
        String from = offender.getFaction() + PAIR;
        String except = pair(offender.getFaction(), besides.getFaction());
        return thisRound(game).entrySet().stream()
                .filter(entry ->
                        entry.getKey().startsWith(from) && !entry.getKey().equals(except))
                .filter(entry -> entry.getValue() >= ANGERED)
                .count();
    }

    public static double spreadFactor(Game game, Player seat, Player target) {
        if (!AiExperiments.enabled(seat, AiExperiments.Experiment.ANGER_SPREAD)) return 1;
        return 1 + SPREAD_STEP * angeredThisRound(game, seat, target);
    }

    private static Map<String, Double> thisRound(Game game) {
        String stored = game.getStoredValue(ROUND_KEY);
        String round = game.getRound() + FIELDS;
        if (!stored.startsWith(round)) return new LinkedHashMap<>();
        return parse(stored.substring(round.length()));
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
        Map<String, Double> assets = assetsNow(game);
        Map<String, Double> before = LAST_ASSETS.getOrDefault(game.getName(), assets);
        LAST_ASSETS.put(game.getName(), assets);
        Map<String, Set<String>> hands = handsNow(game);
        Map<String, Set<String>> handsBefore = LAST_HANDS.getOrDefault(game.getName(), hands);
        LAST_HANDS.put(game.getName(), hands);
        Set<String> seen = seenPresence(game);
        List<Presence> presence = presence(game).stream()
                .filter(found -> !seen.contains(found.key()))
                .toList();
        int processed = processedCards(game);
        int targets = processed(game, TARGETS_KEY);
        CardScan scan = scanCards(game, processed, targets, before, handsBefore);
        List<Pending> waiting = new ArrayList<>(pending(game));
        waiting.addAll(scan.pending());
        List<Cost> impacts = new ArrayList<>();
        List<Pending> still = new ArrayList<>();
        for (Pending open : waiting) {
            Optional<Double> settled = settle(game, open, assets, hands);
            if (settled.isPresent()) {
                if (settled.get() > 0) impacts.add(new Cost(open.offender(), open.victim(), settled.get()));
            } else {
                still.add(open);
            }
        }
        boolean pendingChanged = !still.equals(pending(game));
        boolean cardsMoved = scan.processed() != processed || scan.targets() != targets;
        if (presence.isEmpty() && !cardsMoved && impacts.isEmpty() && !pendingChanged) return;
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
            impacts.forEach(cost -> apply(latest, cost));
            latest.setStoredValue(
                    PENDING_KEY, still.stream().map(Pending::encode).collect(Collectors.joining(ENTRIES)));
        });
    }

    public static void forget(String gameName) {
        LAST_ASSETS.remove(gameName);
        LAST_HANDS.remove(gameName);
    }

    private static Optional<Double> settle(
            Game game, Pending open, Map<String, Double> assets, Map<String, Set<String>> hands) {
        return switch (open.kind()) {
            case ASSETS -> {
                double loss = open.before() - assets.getOrDefault(open.victim(), open.before());
                if (loss >= MIN_IMPACT) yield Optional.of(loss * share(open.detail()));
                yield open.windowClosed(game) ? Optional.of(0.0) : Optional.empty();
            }
            case SPY -> {
                Set<String> lost = new LinkedHashSet<>(List.of(StringUtils.split(open.detail(), HAND)));
                lost.removeAll(hands.getOrDefault(open.victim(), Set.of()));
                if (!lost.isEmpty()) yield Optional.of(stolenValue(game, open.victim(), lost));
                yield open.windowClosed(game) ? Optional.of(0.0) : Optional.empty();
            }
            case ASSASSINATION -> assassination(game, open);
        };
    }

    private static double share(String detail) {
        return NumberUtils.isCreatable(detail) ? Double.parseDouble(detail) : 1.0;
    }

    private static double stolenValue(Game game, String victim, Set<String> lost) {
        Player owner = game.getPlayerFromColorOrFaction(victim);
        if (owner == null) return 0;
        return lost.stream()
                .mapToDouble(alias -> Math.max(0, ActionCardValue.keepValue(game, owner, alias)))
                .max()
                .orElse(0);
    }

    private static Optional<Double> assassination(Game game, Pending open) {
        if (!open.detail().equals(PromisedVotes.agendaKey(game))) return Optional.of(0.0);
        Player victim = game.getPlayerFromColorOrFaction(open.victim());
        if (victim == null) return Optional.of(0.0);
        OptionalDouble loss = AssassinationCost.whenVotesAreIn(game, victim);
        if (loss.isEmpty()) return Optional.empty();
        return Optional.of(loss.getAsDouble() >= MIN_ASSASSINATION_LOSS ? loss.getAsDouble() : 0.0);
    }

    private static Map<String, Set<String>> handsNow(Game game) {
        Map<String, Set<String>> hands = new LinkedHashMap<>();
        for (Player player : game.getRealPlayers()) {
            hands.put(
                    player.getFaction(),
                    new LinkedHashSet<>(player.getActionCards().keySet()));
        }
        return hands;
    }

    private static Map<String, Double> assetsNow(Game game) {
        Map<String, Double> assets = new LinkedHashMap<>();
        for (Player player : game.getRealPlayers()) assets.put(player.getFaction(), PlayerAssets.value(game, player));
        return assets;
    }

    static List<Pending> pending(Game game) {
        List<Pending> found = new ArrayList<>();
        for (String entry : StringUtils.split(game.getStoredValue(PENDING_KEY), ENTRIES)) {
            Pending.decode(entry).ifPresent(found::add);
        }
        return found;
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

    private static CardScan scanCards(
            Game game, int processed, int targets, Map<String, Double> before, Map<String, Set<String>> hands) {
        List<Cost> found = new ArrayList<>();
        List<Pending> waiting = new ArrayList<>();
        List<GameStats.ActionCardTarget> aimed = game.getGameStats().getActionCardTargets();
        for (int index = Math.min(targets, aimed.size()); index < aimed.size(); index++) {
            GameStats.ActionCardTarget target = aimed.get(index);
            if (NOT_MEAN_TARGETS.contains(target.getActionCard())) continue;
            Player player = byTrackedId(game, target.getPlayerId());
            Player victim = byTrackedId(game, target.getTargetId());
            if (player == null || victim == null || player == victim) continue;
            Double flat = FLAT_IMPACT.get(target.getActionCard());
            if (flat != null) {
                found.add(new Cost(player.getFaction(), victim.getFaction(), flat));
                continue;
            }
            double was = before.getOrDefault(victim.getFaction(), PlayerAssets.value(game, victim));
            Kind kind = kindOf(target.getActionCard());
            String detail = switch (kind) {
                case SPY ->
                    String.join(
                            HAND,
                            hands.getOrDefault(
                                    victim.getFaction(), victim.getActionCards().keySet()));
                case ASSASSINATION -> PromisedVotes.agendaKey(game);
                case ASSETS ->
                    IMPACT_SHARE.containsKey(target.getActionCard())
                            ? String.valueOf(IMPACT_SHARE.get(target.getActionCard()))
                            : "";
            };
            waiting.add(new Pending(
                    player.getFaction(),
                    victim.getFaction(),
                    was,
                    game.getGameStats().getActionCardPlays().size(),
                    StringUtils.defaultString(game.getActivePlayerID()),
                    game.getRound(),
                    kind,
                    detail));
        }
        int scanned = scanSabotage(game, processed, found);
        return new CardScan(found, waiting, scanned, aimed.size());
    }

    private static Kind kindOf(String card) {
        if (SPY.equals(card)) return Kind.SPY;
        if (ASSASSINATE.equals(card)) return Kind.ASSASSINATION;
        return Kind.ASSETS;
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
        return parse(game.getStoredValue(KEY));
    }

    private static Map<String, Double> parse(String stored) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (String entry : StringUtils.split(stored, TOTALS)) {
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
