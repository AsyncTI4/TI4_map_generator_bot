package ti4.ai.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.Territory;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.scoring.ScoringPaths;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.scoring.WinRace;
import ti4.ai.scoring.WinRace.Boost;
import ti4.ai.tactical.ExtortionPlans;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class Extortion {

    record Threat(
            String position,
            double demandValue,
            double loss,
            int pointsAtRisk,
            String item,
            int stock,
            boolean resolved) {

        Threat(String position, double demandValue, double loss, int pointsAtRisk) {
            this(position, demandValue, loss, pointsAtRisk, "", 0, false);
        }

        Threat worth(double value) {
            return new Threat(position, value, loss, pointsAtRisk, item, stock, resolved);
        }

        Threat asking(DealItem asked, int victimStock) {
            return new Threat(position, demandValue, loss, pointsAtRisk, asked.engineString(), victimStock, resolved);
        }

        Threat settled() {
            return new Threat(position, demandValue, loss, pointsAtRisk, item, stock, true);
        }
    }

    record Demand(Player victim, Tile target, Threat threat, Deal deal) {}

    static final int MAX_DEMAND = 5;
    static final int MAX_PER_ROUND = 2;
    static final double REPEAT_PENALTY = 2.0;
    static final long AI_REPLY_MILLIS = 5 * 60_000L;
    static final long HUMAN_REPLY_MILLIS = 15 * 60_000L;
    private static final long POLL_MILLIS = 15_000L;
    private static final double SHIP_LOSS_SHARE = 0.5;
    private static final List<UnitType> GROUND_FORCES = List.of(UnitType.Infantry, UnitType.Mech);
    private static final String THREAT_KEY = "aiExtortionThreat|";
    private static final String PAID_KEY = "aiExtortionPaid|";
    private static final String COUNT_KEY = "aiExtortionCount";
    private static final String ROUND = "@";
    private static final String FIELD = "~";
    private static final int FIELDS = 7;
    private static final String DETAIL_SPACE = "fin777";
    private static final String DETAILS_MARK = "_details_";
    private static final String SAVE_REASON = "AI extortion";

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        if (!TradeLegality.isActionPhase(game) || demandsThisRound(game, seat) >= MAX_PER_ROUND) {
            return Optional.empty();
        }
        List<TacticalPlan> ranked = TacticalPlanner.ranked(game, seat);
        if (ranked.isEmpty()) return Optional.empty();
        TacticalPlan attack = ranked.getFirst();
        if (attack.kind() != Kind.ATTACK && attack.kind() != Kind.RAID) return Optional.empty();
        double nextBest = ranked.stream()
                .filter(plan -> !plan.target().equals(attack.target()))
                .mapToDouble(TacticalPlan::score)
                .max()
                .orElse(0);
        Optional<Demand> demand = demand(context, partners, attack, Math.max(0, nextBest));
        if (demand.isEmpty()) return Optional.empty();
        Optional<AiDecision> start =
                OfferBuilder.start(context, demand.get().victim(), demand.get().deal(), Purpose.EXTORTION);
        start.ifPresent(ignored -> persist(game, latest -> {
            Player extorter = latest.getPlayerFromColorOrFaction(seat.getFaction());
            Player victim =
                    latest.getPlayerFromColorOrFaction(demand.get().victim().getFaction());
            if (extorter == null || victim == null) return;
            record(latest, extorter, victim, demand.get().threat());
            extorter.setStoredValue(COUNT_KEY, latest.getRound() + ROUND + (demandsThisRound(latest, extorter) + 1));
        }));
        return start;
    }

    private static int demandsThisRound(Game game, Player seat) {
        String stored = StringUtils.defaultString(seat.getStoredValue(COUNT_KEY));
        if (!String.valueOf(game.getRound()).equals(StringUtils.substringBefore(stored, ROUND))) return 0;
        return TradeMemory.parseInt(StringUtils.substringAfter(stored, ROUND)).orElse(0);
    }

    private static Optional<Demand> demand(
            AiTurnContext context, List<Player> partners, TacticalPlan attack, double nextBest) {
        Game game = context.game();
        Player seat = context.seat();
        Tile target = game.getTileByPosition(attack.target());
        if (target == null || attack.score() <= 0) return Optional.empty();
        boolean scoring = TacticalPlanner.scores(game, seat, attack);
        boolean landing = !attack.landings().isEmpty();
        double needed = attack.score() - nextBest;
        return partners.stream()
                .filter(victim -> holdsSomethingAt(victim, target))
                .filter(victim -> threat(game, seat, victim).isEmpty())
                .filter(victim -> OwnTurnDeals.mayApproach(context, victim))
                .map(victim -> WinRace.canWinThisRound(game, seat, victim)
                        ? winningDemand(game, seat, victim, target, scoring, landing)
                        : demandOf(game, seat, victim, target, needed + repeatPenalty(seat, victim), scoring, landing))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(demand -> demand.threat().demandValue()));
    }

    static Optional<Demand> demandOf(Game game, Player seat, Player victim, Tile target, double needed) {
        return demandOf(game, seat, victim, target, needed, false, true);
    }

    static Optional<Demand> demandOf(
            Game game, Player seat, Player victim, Tile target, double needed, boolean throneOnly) {
        return demandOf(game, seat, victim, target, needed, throneOnly, true);
    }

    static Optional<Demand> demandOf(
            Game game, Player seat, Player victim, Tile target, double needed, boolean throneOnly, boolean landing) {
        Threat threat = threatAt(game, victim, target);
        if (stopsScoring(game, victim, target, landing)) {
            Optional<Demand> throne = throneDemand(game, seat, victim, target, threat, needed);
            if (throne.isPresent()) return throne;
        }
        if (throneOnly) return Optional.empty();
        return tribute(game, seat, victim).stream()
                .filter(item -> worth(game, seat, victim, item) > needed)
                .filter(item -> worth(game, seat, victim, item) <= MAX_DEMAND)
                .filter(item -> pays(game, seat, victim, item, threat))
                .max(Comparator.comparingDouble((DealItem item) -> worth(game, seat, victim, item))
                        .thenComparingDouble(item -> -cost(game, seat, victim, item)))
                .map(item -> demandFor(game, seat, victim, target, threat, item));
    }

    static Optional<Demand> winningDemand(Game game, Player seat, Player victim, Tile target) {
        return winningDemand(game, seat, victim, target, false, true);
    }

    static Optional<Demand> winningDemand(
            Game game, Player seat, Player victim, Tile target, boolean scoring, boolean landing) {
        Threat threat = threatAt(game, victim, target);
        List<DealItem> items = new ArrayList<>();
        if (stopsScoring(game, victim, target, landing) && !throneWins(game, seat)) {
            throneOf(victim).map(alias -> noteFrom(victim, seat, alias)).ifPresent(items::add);
        }
        if (!scoring) {
            tribute(game, seat, victim).stream()
                    .filter(item -> worth(game, seat, victim, item) <= MAX_DEMAND)
                    .forEach(items::add);
        }
        List<OptionalInt> rivals = game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .map(other -> WinRace.finish(game, other, WinRace.feared(game, seat, other, 0)))
                .toList();
        return items.stream()
                .filter(item -> winsFirstWith(game, seat, item, rivals))
                .filter(item -> !AiSeats.isAiSeat(victim) || pays(game, seat, victim, item, threat))
                .findFirst()
                .map(item -> demandFor(game, seat, victim, target, threat, item));
    }

    private static Threat threatAt(Game game, Player victim, Tile target) {
        int points = pointsAtRisk(game, victim, target);
        return new Threat(target.getPosition(), 0, loss(game, victim, target, points), points);
    }

    private static Demand demandFor(Game game, Player seat, Player victim, Tile target, Threat threat, DealItem item) {
        Threat asked = threat.worth(worth(game, seat, victim, item)).asking(item, stockOf(game, victim, item));
        return new Demand(victim, target, asked, Deal.EMPTY.with(item));
    }

    private static boolean stopsScoring(Game game, Player victim, Tile target, boolean landing) {
        if (isHome(game, victim, target)) return landing;
        return pointsAtRisk(game, victim, target) > 0;
    }

    private static boolean throneWins(Game game, Player seat) {
        return seat.getTotalVictoryPoints() + 1 >= GameHorizon.goal(game);
    }

    private static boolean winsFirstWith(Game game, Player seat, DealItem item, List<OptionalInt> rivals) {
        boolean throne = isThrone(game, item);
        int unlocked = item.type() == ItemType.TRADE_GOODS
                ? Math.max(0, SpendUnlock.pointDelta(game, seat, item.amount()))
                : 0;
        OptionalInt mine = WinRace.finish(game, seat, new Boost(throne ? 1 : 0, 0, unlocked, false));
        return rivals.stream().allMatch(theirs -> WinRace.finishesBefore(mine, theirs));
    }

    private static boolean isThrone(Game game, DealItem item) {
        return NotesForTrade.aliasOf(game, item)
                .filter(alias -> alias.endsWith(NoteGiving.THRONE))
                .isPresent();
    }

    private static Optional<Demand> throneDemand(
            Game game, Player seat, Player victim, Tile target, Threat threat, double needed) {
        if (ThroneSale.publicPathToWin(game, seat) || throneWins(game, seat)) return Optional.empty();
        return throneOf(victim)
                .map(alias -> noteFrom(victim, seat, alias))
                .filter(item -> worth(game, seat, victim, item) > needed)
                .filter(item -> pays(game, seat, victim, item, threat))
                .map(item -> demandFor(game, seat, victim, target, threat, item));
    }

    private static DealItem noteFrom(Player victim, Player seat, String alias) {
        return new DealItem(victim.getFaction(), seat.getFaction(), ItemType.PROMISSORY, NotesForTrade.detailOf(alias));
    }

    private static List<DealItem> tribute(Game game, Player seat, Player victim) {
        String from = victim.getFaction();
        String to = seat.getFaction();
        List<DealItem> items = new ArrayList<>();
        for (int price = 1; price <= Math.min(MAX_DEMAND, victim.getTg()); price++) {
            items.add(new DealItem(from, to, ItemType.TRADE_GOODS, String.valueOf(price)));
        }
        for (String kind : ComponentValues.FRAGMENT_KINDS) {
            if (ComponentValues.fragmentsOfKind(victim, kind) > 0) {
                items.add(new DealItem(from, to, ItemType.FRAGMENTS, kind + 1));
            }
        }
        victim.getPromissoryNotes().keySet().stream()
                .filter(victim::ownsPromissoryNote)
                .filter(alias -> NotesForTrade.inHand(victim, alias))
                .filter(alias -> !alias.endsWith(NoteGiving.THRONE))
                .sorted()
                .forEach(alias -> items.add(noteFrom(victim, seat, alias)));
        return items;
    }

    private static double worth(Game game, Player seat, Player victim, DealItem item) {
        return ComponentValues.receive(game, seat, victim, item, Trust.LENDING);
    }

    private static double cost(Game game, Player seat, Player victim, DealItem item) {
        return ComponentValues.give(game, victim, seat, item, Trust.LENDING);
    }

    private static boolean pays(Game game, Player seat, Player victim, DealItem item, Threat threat) {
        String key = THREAT_KEY + victim.getFaction();
        String previous = seat.getStoredValue(key);
        record(game, seat, victim, threat.worth(worth(game, seat, victim, item)));
        try {
            return TradeValue.acceptable(game, victim, seat, Deal.EMPTY.with(item), Trust.LENDING);
        } finally {
            if (StringUtils.isEmpty(previous)) seat.removeStoredValue(key);
            else seat.setStoredValue(key, previous);
        }
    }

    private static int stockOf(Game game, Player victim, DealItem item) {
        return switch (item.type()) {
            case TRADE_GOODS -> victim.getTg();
            case FRAGMENTS -> ComponentValues.fragmentsOfKind(victim, item.fragmentKind());
            case PROMISSORY ->
                NotesForTrade.aliasOf(game, item)
                                .filter(alias -> NotesForTrade.inHand(victim, alias))
                                .isPresent()
                        ? 1
                        : 0;
            default -> 0;
        };
    }

    private static Optional<String> throneOf(Player victim) {
        return victim.getPromissoryNotes().keySet().stream()
                .filter(victim::ownsPromissoryNote)
                .filter(alias -> alias.endsWith(NoteGiving.THRONE))
                .filter(alias -> NotesForTrade.inHand(victim, alias))
                .findFirst();
    }

    static boolean holdsSomethingAt(Player victim, Tile target) {
        return FoWHelper.playerHasUnitsInSystem(victim, target)
                || !victimPlanets(victim, target).isEmpty();
    }

    private static List<Planet> victimPlanets(Player victim, Tile target) {
        return target.getPlanetUnitHolders().stream()
                .filter(planet -> victim.getPlanets().contains(planet.getName()))
                .toList();
    }

    private static boolean isHome(Game game, Player victim, Tile target) {
        return Territory.of(game, victim).zone(target) == Territory.Zone.HOME;
    }

    static int pointsAtRisk(Game game, Player victim, Tile target) {
        if (isHome(game, victim, target)) return ScoringPaths.readyPoints(game, victim);
        return victimPlanets(victim, target).stream()
                .mapToInt(planet -> SpendUnlock.pointsLostByExhausting(game, victim, planet.getName()))
                .max()
                .orElse(0);
    }

    static double loss(Game game, Player victim, Tile target, int pointsAtRisk) {
        double planets = victimPlanets(victim, target).stream()
                .mapToDouble(BoardView::planetValue)
                .sum();
        double ships = SHIP_LOSS_SHARE * BoardView.shipCost(BoardView.space(target), victim);
        return planets + ships + groundLoss(target, victim) + pointsAtRisk * ScoringStakes.vpWorth(game, victim);
    }

    static double groundLoss(Tile target, Player victim) {
        return target.getPlanetUnitHolders().stream()
                .mapToDouble(planet -> PlanetStake.structures(planet, victim)
                        + GROUND_FORCES.stream()
                                .mapToDouble(type -> BoardView.count(planet, victim, type) * unitCost(victim, type))
                                .sum())
                .sum();
    }

    private static double unitCost(Player victim, UnitType type) {
        return BoardView.model(victim, type).map(UnitModel::getCost).orElse(0f);
    }

    static Optional<Threat> threat(Game game, Player extorter, Player victim) {
        String stored = StringUtils.defaultString(extorter.getStoredValue(THREAT_KEY + victim.getFaction()));
        if (!String.valueOf(game.getRound()).equals(StringUtils.substringBefore(stored, ROUND))) {
            return Optional.empty();
        }
        String[] fields = StringUtils.splitPreserveAllTokens(StringUtils.substringAfter(stored, ROUND), FIELD);
        if (fields == null || fields.length != FIELDS) return Optional.empty();
        try {
            return Optional.of(new Threat(
                    fields[0],
                    Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2]),
                    Integer.parseInt(fields[3]),
                    fields[4],
                    Integer.parseInt(fields[5]),
                    Boolean.parseBoolean(fields[6])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static void record(Game game, Player extorter, Player victim, Threat threat) {
        extorter.setStoredValue(
                THREAT_KEY + victim.getFaction(),
                game.getRound()
                        + ROUND
                        + String.join(
                                FIELD,
                                threat.position(),
                                String.valueOf(threat.demandValue()),
                                String.valueOf(threat.loss()),
                                String.valueOf(threat.pointsAtRisk()),
                                threat.item(),
                                String.valueOf(threat.stock()),
                                String.valueOf(threat.resolved())));
    }

    private static Optional<Threat> open(Game game, Player extorter, Player victim) {
        return threat(game, extorter, victim).filter(threat -> !threat.resolved());
    }

    static boolean isTribute(Player extorter, Player victim, Deal deal) {
        return !deal.isEmpty()
                && deal.items().stream()
                        .filter(item -> item.type() != ItemType.DETAILS)
                        .allMatch(item -> item.isFrom(victim.getFaction()) && item.isTo(extorter.getFaction()));
    }

    private static boolean matches(Game game, Threat threat, Player extorter, Player victim, Deal deal) {
        if (!isTribute(extorter, victim, deal)) return false;
        if (threat.item().isEmpty()) return true;
        List<String> asked = DealItem.parse(threat.item(), victim.getFaction(), extorter.getFaction()).stream()
                .map(item -> itemKey(game, item))
                .toList();
        List<String> offered = deal.withoutUnsupported().items().stream()
                .map(item -> itemKey(game, item))
                .toList();
        return asked.equals(offered);
    }

    private static String itemKey(Game game, DealItem item) {
        if (item.type() != ItemType.PROMISSORY) return item.engineString();
        return item.type() + FIELD + NotesForTrade.aliasOf(game, item).orElse(item.detail());
    }

    static boolean isDemand(Game game, Player victim, Player extorter, Deal deal) {
        return open(game, extorter, victim)
                .filter(threat -> matches(game, threat, extorter, victim, deal))
                .isPresent();
    }

    static double relief(Game game, Player victim, Player extorter, Deal deal) {
        return open(game, extorter, victim)
                .filter(threat -> matches(game, threat, extorter, victim, deal))
                .map(Threat::loss)
                .orElse(0.0);
    }

    static int pointsSaved(Game game, Player victim, Player extorter, Deal deal) {
        return open(game, extorter, victim)
                .filter(threat -> matches(game, threat, extorter, victim, deal))
                .map(Threat::pointsAtRisk)
                .orElse(0);
    }

    static boolean isCounter(Game game, Player extorter, Player victim, Deal deal) {
        return open(game, extorter, victim).isPresent() && isTribute(extorter, victim, deal);
    }

    static void addDetails(Game game, Player extorter, Player victim) {
        String prefix = "sending" + extorter.getFaction() + "_receiving" + victim.getFaction() + DETAILS_MARK;
        if (extorter.getTransactionItems().stream().anyMatch(item -> item.startsWith(prefix))) return;
        threat(game, extorter, victim)
                .map(threat -> prefix + detailsText(game, threat).replace(" ", DETAIL_SPACE))
                .ifPresent(item -> persist(game, latest -> {
                    Player holder = latest.getPlayerFromColorOrFaction(extorter.getFaction());
                    if (holder != null && holder.getTransactionItems().stream().noneMatch(item::equals)) {
                        holder.addTransactionItem(item);
                    }
                }));
    }

    static String detailsText(Game game, Threat threat) {
        Tile tile = game.getTileByPosition(threat.position());
        String where = tile == null ? threat.position() : tile.getRepresentationForButtons();
        String text = "If you refuse I will attack " + where + " this turn. If you accept I will not attack it again"
                + " this round.";
        return text.replace("_", "").replace(",", "").replace("\n", "");
    }

    static void accepted(AiTurnContext context, Player victim) {
        Game game = context.game();
        Optional<Threat> threat = open(game, context.seat(), victim);
        if (threat.isPresent() && !delivered(game, context.seat(), victim, threat.get())) {
            refused(context, victim);
            return;
        }
        settle(context, victim);
    }

    static void settle(AiTurnContext context, Player victim) {
        Player seat = context.seat();
        persist(context.game(), latest -> {
            Player extorter = latest.getPlayerFromColorOrFaction(seat.getFaction());
            Player payer = latest.getPlayerFromColorOrFaction(victim.getFaction());
            if (extorter == null || payer == null) return;
            threat(latest, extorter, payer).ifPresent(threat -> {
                ExtortionPlans.spare(latest, extorter, threat.position());
                record(latest, extorter, payer, threat.settled());
            });
            ExtortionPlans.paidBy(latest, extorter, payer);
            extorter.setStoredValue(PAID_KEY + payer.getFaction(), String.valueOf(timesPaid(extorter, payer) + 1));
        });
    }

    private static boolean delivered(Game game, Player extorter, Player victim, Threat threat) {
        if (threat.item().isEmpty()) return true;
        Optional<DealItem> asked = DealItem.parse(threat.item(), victim.getFaction(), extorter.getFaction());
        if (asked.isEmpty()) return true;
        DealItem item = asked.get();
        if (item.type() == ItemType.PROMISSORY) {
            return NotesForTrade.aliasOf(game, item)
                    .filter(alias -> extorter.getPromissoryNotes().containsKey(alias)
                            || extorter.getPromissoryNotesInPlayArea().contains(alias))
                    .isPresent();
        }
        return stockOf(game, victim, item) <= threat.stock() - item.amount();
    }

    static int timesPaid(Player extorter, Player victim) {
        return TradeMemory.parseInt(StringUtils.defaultString(extorter.getStoredValue(PAID_KEY + victim.getFaction())))
                .orElse(0);
    }

    static double repeatPenalty(Player extorter, Player victim) {
        return REPEAT_PENALTY * timesPaid(extorter, victim);
    }

    static void refused(AiTurnContext context, Player victim) {
        Player seat = context.seat();
        persist(context.game(), latest -> {
            Player extorter = latest.getPlayerFromColorOrFaction(seat.getFaction());
            Player refuser = latest.getPlayerFromColorOrFaction(victim.getFaction());
            if (extorter == null || refuser == null) return;
            open(latest, extorter, refuser).ifPresent(threat -> {
                ExtortionPlans.commit(latest, extorter, threat.position(), threat.demandValue());
                record(latest, extorter, refuser, threat.settled());
            });
        });
    }

    private static void persist(Game game, Consumer<Game> change) {
        change.accept(game);
        String name = game.getName();
        if (!GameManager.isValid(name)) return;
        ExecutionLockManager.lock(name, ExecutionLockType.WRITE);
        try {
            Game latest = GameManager.getManagedGame(name).getGame();
            if (latest != game) change.accept(latest);
            GameManager.save(latest, SAVE_REASON);
        } finally {
            ExecutionLockManager.unlock(name, ExecutionLockType.WRITE);
        }
    }

    static long replyTimeout(Player victim) {
        return AiSeats.isAiSeat(victim) ? AI_REPLY_MILLIS : HUMAN_REPLY_MILLIS;
    }

    static Optional<AiDecision> await(AiTurnContext context) {
        if (!context.isActivePlayer() || !TradeLegality.isActionPhase(context.game())) return Optional.empty();
        return TradeLegality.legalPartners(context.game(), context.seat()).stream()
                .map(partner -> Pending.read(context.memory(), partner))
                .flatMap(Optional::stream)
                .filter(pending -> pending.purpose() == Purpose.EXTORTION)
                .map(Pending::expiresAt)
                .min(Long::compare)
                .filter(until -> context.now() < until)
                .map(until -> (AiDecision) new AiDecision.Wait(
                        Math.min(until, context.now() + POLL_MILLIS), "trade: waiting for an answer to its demand"));
    }
}
