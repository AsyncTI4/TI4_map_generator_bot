package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.ToDoubleFunction;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.Pooling;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

// What every seat spent in one self-play game, read from game-state diffs between harness ticks (a tick makes at most
// one press, and every AI press is heard here first, so the press heard since the last observe labels the diff):
// - Leadership: tokens gained and bought (token total after minus before, inside a window that opens on the
//   leadershipGenerateCCButtons press and closes when both "done" buttons are pressed, the seat moves on after
//   finishing its tokens, the phase or round changes, or the game ends). The primary's free tokens (3, capped by
//   reinforcements; LRR 52.2) are not bought; the secondary spends no strategy token (LRR 52.3). Waste is the
//   influence (pooled under Archon's Gift or Xxekir Grom) plus trade goods paid, minus 3 per token bought.
// - Construction (Thunder's Edge te4construction): primaries once per round, of which "produce" when the primary used
//   PRODUCTION instead of placing a structure; follows that spent a strategy token, and follow presses that did not.
// - Space docks placed, per tile, at home or forward, labelled by the seat's Construction role that round (other when
//   it pressed no Construction button).
// - Planets readied during the action phase: by Ggrocuto Rinn (a khraskHeroStep4Ready_ press while the seat's
//   xxchaagent use is open, from its ready -> exhausted until the seat's turn ends; the same handler is shared by
//   Reparations and other effects, which count as other), by Xxekir Grom (skipbuildxxcha placements while the
//   xxchahero-te purge is open), by Diplomacy, or other. Resets of a spend window are not readies. A readied planet
//   that its owner exhausts again in the same round is counted as respent.
// - Per round: raw resources and influence of the planets exhausted outside the agenda phase, their pooled value
//   (max(R, I) per planet under Archon's Gift, R + I under Xxekir Grom of Prophecy of Kings, 0 otherwise), influence
//   exhausted in the agenda phase, and trade-good decreases (trades and transactions included).
final class SpendingTracker {

    static final ConcurrentLinkedQueue<Summary> BATCH = new ConcurrentLinkedQueue<>();

    static final String XXCHA_AGENT = "xxchaagent";
    static final String XXCHA_HERO = "xxchahero-te";
    static final String PRIMARY = "primary";
    static final String SECONDARY = "secondary";
    static final String FOLLOW = "follow";
    static final String OTHER = "other";
    static final String DIPLOMACY = "diplomacy";
    private static final String RESET = "reset";
    private static final String HOME = "home";
    private static final String FORWARD = "forward";

    private static final String KHRASK_READY = "khraskherostep4ready_";
    private static final String HERO_PLACE = "skipbuildxxcha";
    private static final String DIPLOMACY_READY = "diplorefresh2";
    private static final String RESET_SPEND = "resetspend";
    private static final String LEADERSHIP_PRESS = "leadershipgenerateccbuttons";
    private static final String LEADERSHIP_DONE = "deletebuttons_leadership";
    private static final List<String> LEADERSHIP_STEPS =
            List.of("increase_", "resetccs", RESET_SPEND, LEADERSHIP_DONE, LEADERSHIP_PRESS);
    // Leadership pays with influence: its planet and trade-good buttons end in _inf (spend_<planet>_inf).
    private static final List<String> INFLUENCE_PAYMENTS = List.of("spend_", "reducetg_", "reducecomm_");
    private static final String INFLUENCE_SUFFIX = "_inf";
    private static final String CONSTRUCTION_PRODUCE = "constructionprimary_produce";
    private static final List<String> CONSTRUCTION_PRESSES =
            List.of("construction_", "constructionprimary_", "constructionbuild_");
    private static final int LEADERSHIP_FREE_TOKENS = 3;
    private static final int INFLUENCE_PER_TOKEN = 3;

    private static final int RESOURCES = 0;
    private static final int INFLUENCE = 1;
    private static final int POOLED = 2;
    private static final int AGENDA_INFLUENCE = 3;
    private static final int TRADE_GOODS = 4;
    private static final int SPEND_SLOTS = 5;

    record Press(String faction, String handler) {}

    record SeatState(
            int round,
            String phase,
            String active,
            Set<String> exhausted,
            Set<String> owned,
            int tokens,
            int strategy,
            int tradeGoods,
            Map<String, Integer> docksByTile,
            String home,
            String agent,
            String hero) {

        boolean inAction() {
            return "action".equalsIgnoreCase(phase);
        }

        boolean inAgenda() {
            return phase != null && phase.toLowerCase(Locale.ROOT).contains("agenda");
        }
    }

    record Spend(int resources, int influence, int pooled, int agendaInfluence, int tradeGoods) {
        int slot(int index) {
            return switch (index) {
                case RESOURCES -> resources;
                case INFLUENCE -> influence;
                case POOLED -> pooled;
                case AGENDA_INFLUENCE -> agendaInfluence;
                default -> tradeGoods;
            };
        }
    }

    record Summary(
            String game,
            int finalRound,
            Set<String> factions,
            Map<String, Map<String, Integer>> counts,
            Map<String, Map<Integer, Spend>> perRound,
            List<String> lines) {}

    private static final class LeadershipWindow {
        final String kind;
        final int round;
        final String phase;
        final SeatState opened;
        final int free;
        boolean tokensDone;
        boolean planetsDone;

        LeadershipWindow(String kind, SeatState opened, int free) {
            this.kind = kind;
            this.round = opened.round();
            this.phase = opened.phase();
            this.opened = opened;
            this.free = free;
        }
    }

    private record Readied(String source, String presser, int round) {}

    private record Pending(String role, int round) {}

    private final Map<String, SeatState> last = new LinkedHashMap<>();
    private final List<Press> heard = new ArrayList<>();
    private final Map<String, LeadershipWindow> leadership = new HashMap<>();
    private final Set<String> agentOpen = new HashSet<>();
    private final Set<String> heroOpen = new HashSet<>();
    private final Set<String> diplomacyThisRound = new HashSet<>();
    private final Set<String> constructionPrimaries = new HashSet<>();
    private final Map<String, Pending> construction = new HashMap<>();
    private final Map<String, Readied> readiedThisRound = new HashMap<>();
    private final Map<String, Map<String, Integer>> counts = new TreeMap<>();
    private final Map<String, Map<Integer, int[]>> perRound = new TreeMap<>();
    private Game lastGame;
    private String signature = "";
    private int round;

    synchronized void pressed(int round, String faction, String customId, String outcome) {
        if (!"PRESSED".equals(outcome) || faction == null || customId == null) return;
        String handler = handlerOf(customId);
        heard.add(new Press(faction, handler));
        leadershipPressed(faction, handler, customId.regionMatches(true, 0, "FFCC_", 0, "FFCC_".length()));
    }

    synchronized void observe(Game game) {
        if (game == null) return;
        lastGame = game;
        String now = signatureOf(game);
        if (heard.isEmpty() && now.equals(signature)) return;
        signature = now;
        List<Press> presses = List.copyOf(heard);
        heard.clear();
        if (game.getRound() != round) newRound(game.getRound());
        Map<String, SeatState> states = snapshot(game);
        for (Press press : presses) {
            if (press.handler().contains(DIPLOMACY_READY)) diplomacyThisRound.add(press.faction());
        }
        for (Player seat : game.getRealPlayers()) {
            String faction = seat.getFaction();
            SeatState before = last.get(faction);
            SeatState after = states.get(faction);
            if (before == null || after == null) continue;
            closeLeadershipOnPhaseChange(faction, after);
            observeLeaders(faction, before, after);
            observeSpend(game, seat, before, after);
            observeConstruction(game, seat, before, after, presses);
            observeDocks(faction, before, after);
        }
        observeReadies(states, presses);
        closeLeaderUsesAfterTurn(game);
        last.putAll(states);
    }

    // After the harness changes the game itself (fracture entry, relic hand-out), the next diff must not include it.
    synchronized void rebase(Game game) {
        if (game == null) return;
        lastGame = game;
        signature = signatureOf(game);
        last.putAll(snapshot(game));
    }

    synchronized Summary summary(String game) {
        for (String faction : new ArrayList<>(leadership.keySet())) close(faction);
        Map<String, Map<String, Integer>> countsCopy = new TreeMap<>();
        counts.forEach((faction, values) -> countsCopy.put(faction, Map.copyOf(values)));
        Map<String, Map<Integer, Spend>> spendCopy = new TreeMap<>();
        perRound.forEach((faction, rounds) -> {
            Map<Integer, Spend> copy = new TreeMap<>();
            rounds.forEach((r, slots) -> copy.put(r, new Spend(slots[0], slots[1], slots[2], slots[3], slots[4])));
            spendCopy.put(faction, copy);
        });
        Set<String> factions = new TreeSet<>(last.keySet());
        List<String> lines = new ArrayList<>();
        for (String faction : factions) {
            Map<String, Integer> seat = countsCopy.getOrDefault(faction, Map.of());
            Map<Integer, Spend> spend = spendCopy.getOrDefault(faction, Map.of());
            lines.add(faction + " r" + round + ": "
                    + describe(key -> seat.getOrDefault(key, 0), slot -> perRoundOf(spend, slot)));
        }
        return new Summary(game, round, factions, countsCopy, spendCopy, lines);
    }

    synchronized int count(String faction, String key) {
        return counts.getOrDefault(faction, Map.of()).getOrDefault(key, 0);
    }

    synchronized Spend spent(String faction, int inRound) {
        int[] slots = perRound.getOrDefault(faction, Map.of()).get(inRound);
        return slots == null ? new Spend(0, 0, 0, 0, 0) : new Spend(slots[0], slots[1], slots[2], slots[3], slots[4]);
    }

    private void newRound(int now) {
        round = now;
        diplomacyThisRound.clear();
        readiedThisRound.clear();
    }

    private static String handlerOf(String customId) {
        String lower = customId.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("ffcc_")) return lower;
        int factionEnd = lower.indexOf('_', "ffcc_".length());
        return factionEnd < 0 ? lower : lower.substring(factionEnd + 1);
    }

    private static String signatureOf(Game game) {
        StringBuilder builder = new StringBuilder();
        Player active = game.getActivePlayer();
        builder.append(game.getRound())
                .append('|')
                .append(game.getPhaseOfGame())
                .append('|')
                .append(active == null ? "" : active.getFaction());
        for (Player seat : game.getRealPlayers()) {
            builder.append('|')
                    .append(seat.getTg())
                    .append(',')
                    .append(tokens(seat))
                    .append(',')
                    .append(seat.getExhaustedPlanets().size())
                    .append(',')
                    .append(seat.getPlanets().size());
        }
        return builder.toString();
    }

    private static int tokens(Player seat) {
        return seat.getTacticalCC() + seat.getFleetCC() + seat.getStrategicCC();
    }

    private static Map<String, SeatState> snapshot(Game game) {
        Map<String, SeatState> states = new LinkedHashMap<>();
        Player active = game.getActivePlayer();
        for (Player seat : game.getRealPlayers()) {
            Tile home = seat.getHomeSystemTile();
            states.put(
                    seat.getFaction(),
                    new SeatState(
                            game.getRound(),
                            game.getPhaseOfGame(),
                            active == null ? "" : active.getFaction(),
                            new HashSet<>(seat.getExhaustedPlanets()),
                            new HashSet<>(seat.getPlanets()),
                            tokens(seat),
                            seat.getStrategicCC(),
                            seat.getTg(),
                            docksByTile(game, seat),
                            home == null ? "" : home.getPosition(),
                            leaderState(seat, XXCHA_AGENT),
                            leaderState(seat, XXCHA_HERO)));
        }
        return states;
    }

    private static Map<String, Integer> docksByTile(Game game, Player seat) {
        Map<String, Integer> docks = new HashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            int count = BoardView.countInTile(tile, seat, UnitType.Spacedock);
            if (count > 0) docks.put(tile.getPosition(), count);
        }
        return docks;
    }

    private static String leaderState(Player seat, String id) {
        Leader leader = seat.getLeader(id).orElse(null);
        if (leader == null) return "gone";
        if (leader.isLocked()) return "locked";
        if (leader.isActive()) return "active";
        return leader.isExhausted() ? "exhausted" : "ready";
    }

    private void observeLeaders(String faction, SeatState before, SeatState after) {
        if ("ready".equals(before.agent()) && "exhausted".equals(after.agent())) {
            agentOpen.add(faction);
            add(faction, "agent.exhausts", 1);
        }
        if (("ready".equals(before.hero()) || "active".equals(before.hero())) && "gone".equals(after.hero())) {
            heroOpen.add(faction);
            add(faction, "hero.uses", 1);
        }
    }

    private void closeLeaderUsesAfterTurn(Game game) {
        Player active = game.getActivePlayer();
        String activeFaction = active == null ? "" : active.getFaction();
        boolean action = "action".equalsIgnoreCase(game.getPhaseOfGame());
        agentOpen.removeIf(faction -> !action || !faction.equals(activeFaction));
        heroOpen.removeIf(faction -> !action || !faction.equals(activeFaction));
    }

    private void observeSpend(Game game, Player seat, SeatState before, SeatState after) {
        String faction = seat.getFaction();
        Pooling pooling = Pooling.of(seat);
        int[] spend = perRound.computeIfAbsent(faction, ignored -> new TreeMap<>())
                .computeIfAbsent(after.round(), ignored -> new int[SPEND_SLOTS]);
        for (String planet : newlyExhausted(before, after)) {
            Planet info = game.getPlanetsInfo().get(planet);
            if (info != null) {
                int resources = Math.max(0, info.getResources());
                int influence = Math.max(0, info.getInfluence());
                if (after.inAgenda()) {
                    spend[AGENDA_INFLUENCE] += influence;
                } else {
                    spend[RESOURCES] += resources;
                    spend[INFLUENCE] += influence;
                    spend[POOLED] += pooling == Pooling.SEPARATE ? 0 : pooling.resourcesOf(info);
                }
            }
            respent(faction, planet, after.round());
        }
        int tradeGoods = before.tradeGoods() - after.tradeGoods();
        if (tradeGoods > 0) spend[TRADE_GOODS] += tradeGoods;
    }

    private void respent(String owner, String planet, int inRound) {
        Readied readied = readiedThisRound.remove(owner + "|" + planet);
        if (readied == null || readied.round() != inRound) return;
        add(readied.presser(), "respent." + readied.source(), 1);
        if (leadership.containsKey(owner)) add(readied.presser(), "respent." + readied.source() + ".leadership", 1);
    }

    private static Set<String> newlyExhausted(SeatState before, SeatState after) {
        Set<String> planets = new TreeSet<>(after.exhausted());
        planets.removeAll(before.exhausted());
        planets.retainAll(after.owned());
        return planets;
    }

    private static Set<String> newlyReadied(SeatState before, SeatState after) {
        Set<String> planets = new TreeSet<>(before.exhausted());
        planets.removeAll(after.exhausted());
        planets.retainAll(after.owned());
        return planets;
    }

    private void observeReadies(Map<String, SeatState> states, List<Press> presses) {
        Set<String> agentsUsed = new HashSet<>();
        for (Map.Entry<String, SeatState> entry : states.entrySet()) {
            String owner = entry.getKey();
            SeatState before = last.get(owner);
            SeatState after = entry.getValue();
            if (before == null || before.round() != after.round() || !before.inAction() || !after.inAction()) continue;
            for (String planet : newlyReadied(before, after)) {
                Readied source = sourceOf(owner, presses, after.round());
                if (RESET.equals(source.source())) continue;
                add(source.presser(), "readies." + source.source(), 1);
                if (!source.presser().equals(owner)) add(source.presser(), "readies." + source.source() + ".others", 1);
                if (XXCHA_AGENT.equals(source.source())) agentsUsed.add(source.presser());
                readiedThisRound.put(owner + "|" + planet, source);
            }
        }
        agentOpen.removeAll(agentsUsed);
    }

    private Readied sourceOf(String owner, List<Press> presses, int inRound) {
        for (Press press : presses) {
            String handler = press.handler();
            if (handler.startsWith(KHRASK_READY) && agentOpen.contains(press.faction()))
                return new Readied(XXCHA_AGENT, press.faction(), inRound);
            if (handler.contains(HERO_PLACE)
                    && heroOpen.contains(press.faction())
                    && press.faction().equals(owner)) return new Readied(XXCHA_HERO, owner, inRound);
            if (handler.startsWith(RESET_SPEND)) return new Readied(RESET, owner, inRound);
        }
        if (diplomacyThisRound.contains(owner)) return new Readied(DIPLOMACY, owner, inRound);
        return new Readied(OTHER, owner, inRound);
    }

    private void observeConstruction(Game game, Player seat, SeatState before, SeatState after, List<Press> presses) {
        String faction = seat.getFaction();
        for (Press press : presses) {
            if (!press.faction().equals(faction) || !isConstruction(press.handler())) continue;
            String role = holds(game, seat, StrategyCard.CONSTRUCTION) ? PRIMARY : FOLLOW;
            construction.put(faction, new Pending(role, after.round()));
            if (PRIMARY.equals(role)) {
                if (constructionPrimaries.add(faction + "|" + after.round()))
                    add(faction, "construction.primary.uses", 1);
                if (press.handler().startsWith(CONSTRUCTION_PRODUCE)) add(faction, "construction.primary.produce", 1);
                continue;
            }
            int spent = before.strategy() - after.strategy();
            if (spent > 0) {
                add(faction, "construction.follow.uses", 1);
                add(faction, "construction.follow.tokens", spent);
            } else {
                add(faction, "construction.follow.withoutToken", 1);
            }
        }
    }

    private static boolean isConstruction(String handler) {
        return CONSTRUCTION_PRESSES.stream().anyMatch(handler::startsWith);
    }

    private void observeDocks(String faction, SeatState before, SeatState after) {
        Pending pending = construction.get(faction);
        String role = pending != null && pending.round() == after.round() ? pending.role() : OTHER;
        for (Map.Entry<String, Integer> tile : after.docksByTile().entrySet()) {
            int placed = tile.getValue() - before.docksByTile().getOrDefault(tile.getKey(), 0);
            if (placed <= 0) continue;
            String where = tile.getKey().equals(after.home()) ? HOME : FORWARD;
            add(faction, "docks." + where + "." + role, placed);
        }
    }

    private static boolean holds(Game game, Player seat, StrategyCard card) {
        return seat.getSCs().stream().anyMatch(initiative -> StrategyCard.of(game, initiative) == card);
    }

    // The owned (FFCC_) "done" button finishes the tokens, the unowned one the planets paid.
    private void leadershipPressed(String faction, String handler, boolean owned) {
        LeadershipWindow window = leadership.get(faction);
        if (handler.startsWith(LEADERSHIP_PRESS)) {
            if (window != null) close(faction);
            open(faction);
            return;
        }
        if (window == null) return;
        if (handler.startsWith(LEADERSHIP_DONE)) {
            if (owned) {
                window.tokensDone = true;
            } else {
                window.planetsDone = true;
            }
            if (window.tokensDone && window.planetsDone) close(faction);
            return;
        }
        if (window.tokensDone && !isLeadershipStep(handler)) close(faction);
    }

    private static boolean isLeadershipStep(String handler) {
        if (LEADERSHIP_STEPS.stream().anyMatch(handler::startsWith)) return true;
        return handler.endsWith(INFLUENCE_SUFFIX) && INFLUENCE_PAYMENTS.stream().anyMatch(handler::startsWith);
    }

    private void open(String faction) {
        SeatState state = last.get(faction);
        Player seat = lastGame == null ? null : lastGame.getPlayerFromColorOrFaction(faction);
        if (state == null || seat == null) return;
        String kind = leadershipKind(lastGame, seat);
        int free = PRIMARY.equals(kind)
                ? Math.min(LEADERSHIP_FREE_TOKENS, StrategyCardRules.reinforcements(lastGame, seat))
                : 0;
        leadership.put(faction, new LeadershipWindow(kind, state, free));
    }

    private static String leadershipKind(Game game, Player seat) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) return OTHER;
        Optional<Integer> card = game.getPlayedSCs().stream()
                .filter(initiative -> StrategyCard.of(game, initiative) == StrategyCard.LEADERSHIP)
                .findFirst();
        if (card.isEmpty()) return OTHER;
        if (seat.getSCs().contains(card.get())) return seat.equals(game.getActivePlayer()) ? PRIMARY : OTHER;
        return SECONDARY;
    }

    private void closeLeadershipOnPhaseChange(String faction, SeatState after) {
        LeadershipWindow window = leadership.get(faction);
        if (window == null) return;
        if (window.round != after.round() || !java.util.Objects.equals(window.phase, after.phase())) close(faction);
    }

    private void close(String faction) {
        LeadershipWindow window = leadership.remove(faction);
        SeatState end = last.get(faction);
        if (window == null || end == null) return;
        Player seat = lastGame == null ? null : lastGame.getPlayerFromColorOrFaction(faction);
        Pooling pooling = seat == null ? Pooling.SEPARATE : Pooling.of(seat);
        int gained = end.tokens() - window.opened.tokens();
        int influence = 0;
        int pooled = 0;
        for (String planet : newlyExhausted(window.opened, end)) {
            Planet info = lastGame == null ? null : lastGame.getPlanetsInfo().get(planet);
            if (info == null) continue;
            influence += Math.max(0, info.getInfluence());
            pooled += pooling.influenceOf(info);
        }
        int tradeGoods = Math.max(0, window.opened.tradeGoods() - end.tradeGoods());
        String prefix = "leadership." + window.kind + ".";
        add(faction, prefix + "uses", 1);
        add(faction, prefix + "gained", gained);
        add(faction, prefix + "influence", influence);
        add(faction, prefix + "pooled", pooled);
        add(faction, prefix + "tg", tradeGoods);
        if (OTHER.equals(window.kind)) return;
        int bought = Math.max(0, gained - window.free);
        add(faction, prefix + "bought", bought);
        add(faction, prefix + "wasted", Math.max(0, pooled + tradeGoods - INFLUENCE_PER_TOKEN * bought));
    }

    private void add(String faction, String key, int amount) {
        if (amount == 0) return;
        counts.computeIfAbsent(faction, ignored -> new TreeMap<>()).merge(key, amount, Integer::sum);
    }

    private static Map<Integer, Double> perRoundOf(Map<Integer, Spend> spend, int slot) {
        Map<Integer, Double> values = new TreeMap<>();
        spend.forEach((r, value) -> values.put(r, (double) value.slot(slot)));
        return values;
    }

    interface PerRound {
        Map<Integer, Double> of(int slot);
    }

    static String describe(ToDoubleFunction<String> value, PerRound rounds) {
        StringBuilder text = new StringBuilder("leadership");
        for (String kind : List.of(PRIMARY, SECONDARY)) {
            String prefix = "leadership." + kind + ".";
            text.append(String.format(
                    " %s %s (gained %s, bought %s, influence %s, pooled %s, tg %s, wasted %s);",
                    kind,
                    number(value.applyAsDouble(prefix + "uses")),
                    number(value.applyAsDouble(prefix + "gained")),
                    number(value.applyAsDouble(prefix + "bought")),
                    number(value.applyAsDouble(prefix + "influence")),
                    number(value.applyAsDouble(prefix + "pooled")),
                    number(value.applyAsDouble(prefix + "tg")),
                    number(value.applyAsDouble(prefix + "wasted"))));
        }
        text.append(String.format(
                " other %s (gained %s, influence %s, tg %s)",
                number(value.applyAsDouble("leadership.other.uses")),
                number(value.applyAsDouble("leadership.other.gained")),
                number(value.applyAsDouble("leadership.other.influence")),
                number(value.applyAsDouble("leadership.other.tg"))));
        text.append(String.format(
                " | construction primaries %s (produce %s), follows %s (strategy tokens %s), follow presses without a"
                        + " token %s",
                number(value.applyAsDouble("construction.primary.uses")),
                number(value.applyAsDouble("construction.primary.produce")),
                number(value.applyAsDouble("construction.follow.uses")),
                number(value.applyAsDouble("construction.follow.tokens")),
                number(value.applyAsDouble("construction.follow.withoutToken"))));
        for (String where : List.of(HOME, FORWARD)) {
            String prefix = "docks." + where + ".";
            double primary = value.applyAsDouble(prefix + PRIMARY);
            double follow = value.applyAsDouble(prefix + FOLLOW);
            double other = value.applyAsDouble(prefix + OTHER);
            text.append(String.format(
                    "%s docks %s %s (primary %s, follow %s, other %s)",
                    HOME.equals(where) ? " |" : ",",
                    where,
                    number(primary + follow + other),
                    number(primary),
                    number(follow),
                    number(other)));
        }
        text.append(String.format(
                " | readies xxchaagent %s of %s agent exhausts (on other seats' planets %s), xxchahero-te %s over %s"
                        + " uses, diplomacy %s, other %s",
                number(value.applyAsDouble("readies." + XXCHA_AGENT)),
                number(value.applyAsDouble("agent.exhausts")),
                number(value.applyAsDouble("readies." + XXCHA_AGENT + ".others")),
                number(value.applyAsDouble("readies." + XXCHA_HERO)),
                number(value.applyAsDouble("hero.uses")),
                number(value.applyAsDouble("readies." + DIPLOMACY)),
                number(value.applyAsDouble("readies." + OTHER))));
        double respentInLeadership = 0;
        for (String source : List.of(XXCHA_AGENT, XXCHA_HERO, DIPLOMACY, OTHER))
            respentInLeadership += value.applyAsDouble("respent." + source + ".leadership");
        text.append(String.format(
                "; respent same round xxchaagent %s, xxchahero-te %s, diplomacy %s, other %s (inside Leadership %s)",
                number(value.applyAsDouble("respent." + XXCHA_AGENT)),
                number(value.applyAsDouble("respent." + XXCHA_HERO)),
                number(value.applyAsDouble("respent." + DIPLOMACY)),
                number(value.applyAsDouble("respent." + OTHER)),
                number(respentInLeadership)));
        text.append(String.format(
                " | per round resources %s, influence %s, pooled %s, agenda influence %s, tg decreases %s",
                rounds(rounds.of(RESOURCES)),
                rounds(rounds.of(INFLUENCE)),
                rounds(rounds.of(POOLED)),
                rounds(rounds.of(AGENDA_INFLUENCE)),
                rounds(rounds.of(TRADE_GOODS))));
        return text.toString();
    }

    private static String rounds(Map<Integer, Double> values) {
        Map<Integer, String> shown = new TreeMap<>();
        values.forEach((r, value) -> shown.put(r, number(value)));
        return shown.toString();
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    // Per faction over the batch: every count averaged over the games the faction was seated in, and each round's
    // spending averaged over the seated games that reached that round.
    static void printBatch() {
        List<Summary> summaries = new ArrayList<>(BATCH);
        if (summaries.isEmpty()) return;
        Map<String, List<Summary>> byFaction = new TreeMap<>();
        for (Summary summary : summaries) {
            for (String faction : summary.factions())
                byFaction.computeIfAbsent(faction, ignored -> new ArrayList<>()).add(summary);
        }
        System.out.println("SPENDING over " + summaries.size() + " games");
        for (Map.Entry<String, List<Summary>> entry : byFaction.entrySet()) {
            String faction = entry.getKey();
            List<Summary> games = entry.getValue();
            System.out.println("SPENDING " + faction + " seated in " + games.size() + " games");
            ToDoubleFunction<String> average = key -> games.stream()
                    .mapToInt(game ->
                            game.counts().getOrDefault(faction, Map.of()).getOrDefault(key, 0))
                    .average()
                    .orElse(0);
            PerRound rounds = slot -> averagePerRound(games, faction, slot);
            System.out.println("SPENDING   " + faction + " " + describe(average, rounds));
        }
    }

    private static Map<Integer, Double> averagePerRound(List<Summary> games, String faction, int slot) {
        int lastRound = games.stream().mapToInt(Summary::finalRound).max().orElse(0);
        Map<Integer, Double> averages = new TreeMap<>();
        for (int r = 1; r <= lastRound; r++) {
            int inRound = r;
            averages.put(
                    r,
                    games.stream()
                            .filter(game -> game.finalRound() >= inRound)
                            .mapToInt(game -> {
                                Spend spend = game.perRound()
                                        .getOrDefault(faction, Map.of())
                                        .get(inRound);
                                return spend == null ? 0 : spend.slot(slot);
                            })
                            .average()
                            .orElse(0));
        }
        return averages;
    }
}
