package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.DoubleSupplier;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.eval.FracturePrize;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.secrets.SecretOdds.CardChance;
import ti4.ai.tactical.PlanetTake;
import ti4.ai.tactical.StyxTake;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class WinOdds {

    public static final double MAJORITY = 0.5;
    private static final int ONE_POINT = 1;
    private static final int STATUS_PUBLIC_SCORES = 1;
    private static final int IMPERIAL_PUBLIC_SCORES = 1;
    private static final int STATUS_SECRET_SCORES = 1;
    private static final int TECH_SPECIALTY_COLORS = 4;
    private static final String BOOK_OF_LATVINIA = "bookoflatvinia";
    private static final String CROWN_OF_EMPHIDIA = "emphidia";
    private static final String TOMB_OF_EMPHIDIA = "attachment_tombofemphidia.png";
    private static final ThreadLocal<Boolean> PRICING_MECATOL_TAKE = ThreadLocal.withInitial(() -> false);

    public record Odds(double duringAction, double byStatusScoring, double byStatusEnd) {

        static final Odds CERTAIN = new Odds(1, 1, 1);
    }

    public record Loss(Set<String> planets, String system, boolean shipsStay) {

        public static final Loss NONE = new Loss(Set.of(), "", false);

        public Loss {
            planets = Set.copyOf(planets);
        }

        public static Loss planet(String planet) {
            return new Loss(Set.of(planet), "", false);
        }

        public static Loss ships(String position) {
            return new Loss(Set.of(), position, false);
        }

        public static Loss locked(String position) {
            return new Loss(Set.of(), position, true);
        }

        public static Loss holdings(Collection<String> planets, String shipsLostIn) {
            return new Loss(new HashSet<>(planets), shipsLostIn, false);
        }

        public boolean isNone() {
            return planets.isEmpty() && system.isEmpty();
        }

        boolean losesShips() {
            return !system.isEmpty() && !shipsStay;
        }

        boolean stopsShips() {
            return !system.isEmpty();
        }
    }

    public static Odds of(Game game, Player viewer, Player rival) {
        return of(game, viewer, rival, 0);
    }

    public static Odds of(Game game, Player viewer, Player rival, int immediatePoints) {
        return DecisionMemo.remember(
                () -> compute(game, viewer, rival, immediatePoints, Loss.NONE),
                "win.odds",
                viewer,
                rival,
                immediatePoints,
                DecidingSeat.knowsHandOf(viewer),
                game.getRound());
    }

    public static Odds without(Game game, Player viewer, Player rival, Loss loss) {
        if (loss.isNone()) return of(game, viewer, rival);
        return DecisionMemo.remember(
                () -> compute(game, viewer, rival, 0, loss),
                "win.odds.without",
                viewer,
                rival,
                loss,
                DecidingSeat.knowsHandOf(viewer),
                game.getRound());
    }

    public static double finishesBeforeWithout(Game game, Player viewer, Player rival, OptionalInt mine, Loss loss) {
        if (rival.isNeutral() || rival.isEliminated() || game.getVp() <= 0) return 0;
        return finishesBefore(without(game, viewer, rival, loss), rival, mine);
    }

    public static double reachesGoal(Game game, Player viewer, Player rival) {
        return reachesGoal(game, viewer, rival, 0);
    }

    public static double reachesGoal(Game game, Player viewer, Player rival, int immediatePoints) {
        if (rival.isNeutral() || rival.isEliminated() || game.getVp() <= 0) return 0;
        return of(game, viewer, rival, immediatePoints).byStatusEnd();
    }

    public static double finishesBefore(Game game, Player viewer, Player rival, OptionalInt mine) {
        return finishesBefore(game, viewer, rival, mine, 0);
    }

    public static double finishesBefore(Game game, Player viewer, Player rival, OptionalInt mine, int immediatePoints) {
        if (rival.isNeutral() || rival.isEliminated() || game.getVp() <= 0) return 0;
        return finishesBefore(of(game, viewer, rival, immediatePoints), rival, mine);
    }

    private static double finishesBefore(Odds odds, Player rival, OptionalInt mine) {
        if (mine.isEmpty()) return odds.byStatusEnd();
        int myFinish = mine.getAsInt();
        if (myFinish <= WinRace.DURING_ACTION_PHASE) return 0;
        boolean scoresEarlier = WinRace.statusFinish(rival) < myFinish;
        return odds.duringAction() + (scoresEarlier ? odds.byStatusScoring() - odds.duringAction() : 0);
    }

    public static boolean outrunsEveryRival(Game game, Player seat, OptionalInt mine, Map<Player, Integer> given) {
        if (mine.isEmpty()) return false;
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && !other.isEliminated())
                .allMatch(other -> finishesBefore(game, seat, other, mine, given.getOrDefault(other, 0)) < MAJORITY);
    }

    private static Odds compute(Game game, Player viewer, Player rival, int immediatePoints, Loss loss) {
        try (Thinking.Span timing = Thinking.measure("winOdds.compute")) {
            Odds held = compute(game, viewer, rival, immediatePoints, loss, Optional.empty());
            if (held == Odds.CERTAIN || !WinRace.weighsFeasibility(viewer)) return held;
            Optional<String> contested = PlanetTake.contestedMecatol(game, rival);
            if (contested.isEmpty()) return held;
            double take = mecatolTake(game, viewer, rival, loss);
            if (take <= 0) return held;
            return blend(compute(game, viewer, rival, immediatePoints, loss, contested), held, take);
        }
    }

    private static double mecatolTake(Game game, Player viewer, Player rival, Loss loss) {
        Tile stopped = loss.stopsShips() ? game.getTileByPosition(loss.system()) : null;
        if (stopped == null) return outsideAnotherTake(() -> PlanetTake.mecatolThisRound(game, viewer, rival));
        return outsideAnotherTake(() -> PlanetTake.mecatolThisRoundWithout(game, viewer, rival, stopped));
    }

    static double outsideAnotherTake(DoubleSupplier take) {
        if (PRICING_MECATOL_TAKE.get()) return 0;
        PRICING_MECATOL_TAKE.set(true);
        try {
            return take.getAsDouble();
        } finally {
            PRICING_MECATOL_TAKE.set(false);
        }
    }

    private static Odds blend(Odds taken, Odds held, double take) {
        return new Odds(
                take * taken.duringAction() + (1 - take) * held.duringAction(),
                take * taken.byStatusScoring() + (1 - take) * held.byStatusScoring(),
                take * taken.byStatusEnd() + (1 - take) * held.byStatusEnd());
    }

    private static Odds compute(
            Game game, Player viewer, Player rival, int immediatePoints, Loss loss, Optional<String> takes) {
        int missing = game.getVp() - rival.getTotalVictoryPoints() - immediatePoints + heldPointsLost(rival, loss);
        if (missing <= 0) return Odds.CERTAIN;
        Board board = Board.of(game, rival, loss, takes);
        boolean acts = !rival.isPassed();
        boolean imperial = ScoringStakes.holdsUnplayedImperial(game, rival);
        boolean mecatol = takes.isPresent() || (rival.controlsMecatol(true) && !losesMecatol(game, loss));
        Grid grid = Grid.start(missing);
        grid = grid.with(publics(game, rival, imperial, missing, board));
        grid = grid.with(secrets(game, viewer, rival, missing, acts, board));
        if (imperial && mecatol) grid = grid.with(Grid.certain(missing, ONE_POINT, 0));
        if (acts) {
            grid = grid.with(Grid.chance(
                    missing, custodiansPoints(imperial, mecatol), 0, custodians(game, viewer, rival, loss)));
            grid = grid.with(Grid.chance(missing, ONE_POINT, 0, styxTake(game, rival, loss)));
        }
        if (acts && latviniaReady(game, rival, loss)) grid = grid.with(Grid.certain(missing, ONE_POINT, 0));
        int crown = crownReady(game, rival, loss) ? ONE_POINT : 0;
        return new Odds(grid.actionAtLeast(missing), grid.totalAtLeast(missing, 0), grid.totalAtLeast(missing, crown));
    }

    private static int heldPointsLost(Player rival, Loss loss) {
        boolean losesStyx = loss.planets().contains(FracturePrize.STYX)
                && rival.getPlanets().contains(FracturePrize.STYX);
        return losesStyx ? ONE_POINT : 0;
    }

    private static boolean losesMecatol(Game game, Loss loss) {
        return loss.planets().stream().anyMatch(game.mecatols()::contains);
    }

    private static double styxTake(Game game, Player rival, Loss loss) {
        double now = StyxTake.thisRound(game, rival);
        if (now <= 0 || !loss.stopsShips()) return now;
        Tile stopped = game.getTileByPosition(loss.system());
        return stopped == null ? now : StyxTake.thisRoundWithout(game, rival, stopped);
    }

    private static boolean latviniaReady(Game game, Player rival, Loss loss) {
        if (!rival.hasRelic(BOOK_OF_LATVINIA)) return false;
        Set<String> specialties = new HashSet<>();
        for (String name : rival.getPlanets()) {
            if (loss.planets().contains(name)) continue;
            Planet planet = game.getUnitHolderFromPlanet(name);
            if (planet != null) specialties.addAll(planet.getTechSpecialities());
        }
        return specialties.size() >= TECH_SPECIALTY_COLORS;
    }

    private static boolean crownReady(Game game, Player rival, Loss loss) {
        if (!rival.hasRelic(CROWN_OF_EMPHIDIA)) return false;
        return rival.getPlanets().stream()
                .filter(name -> !loss.planets().contains(name))
                .map(game::getUnitHolderFromPlanet)
                .anyMatch(planet -> planet != null && planet.getTokenList().contains(TOMB_OF_EMPHIDIA));
    }

    private static int custodiansPoints(boolean imperial, boolean mecatol) {
        return ONE_POINT + (imperial && !mecatol ? ONE_POINT : 0);
    }

    private static double custodians(Game game, Player viewer, Player rival, Loss loss) {
        if (!CustodiansRace.open(game)) return 0;
        CustodiansRace.Snapshot race = CustodiansRace.of(game, viewer);
        if (!race.canLandThisRound(rival)) return 0;
        if (loss.stopsShips() && !race.canLandThisRoundWithout(rival, loss.system())) return 0;
        OptionalInt arrival = race.arrival(rival);
        if (arrival.isEmpty() || arrival.getAsInt() != game.getRound()) return 0;
        return race.winChance(rival, arrival.getAsInt(), CustodiansRace.Order.KNOWN);
    }

    private static final class Board {

        private static final Board UNCHANGED = new Board(Loss.NONE, null, null, false, false);
        private static final Set<String> NOMADIC_ABILITIES = Set.of("nomadic", "mobile_command");
        private static final String NOMADIC_TECH = "tf-nomadic";

        private final Loss loss;
        private final ObjectiveValue view;
        private final Footprint after;
        private final boolean homeLost;
        private final boolean gains;

        private Board(Loss loss, ObjectiveValue view, Footprint after, boolean homeLost, boolean gains) {
            this.loss = loss;
            this.view = view;
            this.after = after;
            this.homeLost = homeLost;
            this.gains = gains;
        }

        static Board of(Game game, Player rival, Loss loss, Optional<String> gained) {
            if (loss.isNone() && gained.isEmpty()) return UNCHANGED;
            ObjectiveValue view =
                    DecisionMemo.remember(() -> ObjectiveValue.publicOnly(game, rival), "win.odds.board", rival);
            Footprint after = gained.map(view.before()::withPlanet).orElse(view.before());
            for (String planet : loss.planets()) after = after.withoutPlanet(planet);
            if (loss.losesShips()) after = after.withoutShipsIn(loss.system());
            return new Board(loss, view, after, losesHomePlanet(rival, loss), gained.isPresent());
        }

        private static boolean losesHomePlanet(Player rival, Loss loss) {
            if (NOMADIC_ABILITIES.stream().anyMatch(rival::hasAbility) || rival.hasTech(NOMADIC_TECH)) return false;
            Tile home = rival.getHomeSystemTile();
            if (home == null) return false;
            return home.getPlanetUnitHolders().stream()
                    .anyMatch(planet -> !planet.isSpaceStation()
                            && rival.getPlanets().contains(planet.getName())
                            && loss.planets().contains(planet.getName()));
        }

        boolean changed() {
            return view != null;
        }

        int drop(String id) {
            return Math.max(0, -change(id));
        }

        int rise(String id) {
            return gains ? Math.max(0, change(id)) : 0;
        }

        private int change(String id) {
            if (view == null) return 0;
            int now = view.progress(id, view.before());
            int then = view.progress(id, after);
            if (now == ObjectiveValue.NOT_MODELLED || then == ObjectiveValue.NOT_MODELLED) return 0;
            return then - now;
        }

        Wallet wallet(Wallet wallet) {
            if (loss.planets().isEmpty()) return wallet;
            return wallet.without(new Wallet.Payment(List.copyOf(loss.planets()), List.of(), 0, 0));
        }
    }

    private record Candidate(int points, double chance) {}

    private static Grid publics(Game game, Player rival, boolean imperial, int missing, Board board) {
        List<Candidate> candidates = board.homeLost ? List.of() : publicCandidates(game, rival, board);
        int picks = STATUS_PUBLIC_SCORES + (imperial ? IMPERIAL_PUBLIC_SCORES : 0);
        double[][][] byPicks = new double[picks + 1][missing + 1][missing + 1];
        byPicks[0][0][0] = 1;
        for (Candidate candidate : candidates) {
            double[][][] next = new double[picks + 1][missing + 1][missing + 1];
            for (int taken = 0; taken <= picks; taken++) {
                for (int action = 0; action <= missing; action++) {
                    for (int status = 0; status <= missing; status++) {
                        double p = byPicks[taken][action][status];
                        if (p == 0) continue;
                        if (taken == picks) {
                            next[taken][action][status] += p;
                            continue;
                        }
                        next[taken][action][status] += p * (1 - candidate.chance());
                        boolean duringAction = imperial && taken == 0;
                        int a = duringAction ? cap(action + candidate.points(), missing) : action;
                        int s = duringAction ? status : cap(status + candidate.points(), missing);
                        next[taken + 1][a][s] += p * candidate.chance();
                    }
                }
            }
            byPicks = next;
        }
        Grid grid = Grid.empty(missing);
        for (double[][] layer : byPicks) grid.addAll(layer);
        return grid;
    }

    private static List<Candidate> publicCandidates(Game game, Player rival, Board board) {
        if (!Helper.canPlayerScorePOs(game, rival)) return List.of();
        List<Candidate> candidates = new ArrayList<>();
        Wallet wallet = board.wallet(WinRace.projectedWallet(game, rival));
        int bestSpend = 0;
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (ObjectivePolicy.hasScored(game, rival, id)) continue;
            boolean ready = WinRace.qualifies(game, rival, wallet, id);
            if (ObjectiveCatalog.isSpend(id)) {
                if (ready) bestSpend = Math.max(bestSpend, ObjectivePolicy.victoryPoints(id));
                continue;
            }
            int drop = board.drop(id);
            if (ready && drop == 0) {
                candidates.add(new Candidate(ObjectivePolicy.victoryPoints(id), 1));
                continue;
            }
            int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
            if (threshold <= 0) continue;
            int rise = board.rise(id);
            int progress = ListPlayerInfoService.getPlayerProgressOnObjective(id, game, rival) - drop + rise;
            if (progress >= threshold) {
                if (ready || progress - rise < threshold)
                    candidates.add(new Candidate(ObjectivePolicy.victoryPoints(id), 1));
                continue;
            }
            double chance = SecretOdds.readinessAt(progress, threshold);
            if (chance > 0) candidates.add(new Candidate(ObjectivePolicy.victoryPoints(id), chance));
        }
        if (bestSpend > 0) candidates.add(new Candidate(bestSpend, 1));
        candidates.sort(Comparator.comparingInt(Candidate::points)
                .reversed()
                .thenComparing(Comparator.comparingDouble(Candidate::chance).reversed()));
        return candidates;
    }

    private static Grid secrets(Game game, Player viewer, Player rival, int missing, boolean acts, Board board) {
        double[][] byStatusTaken = new double[STATUS_SECRET_SCORES + 1][missing + 1];
        byStatusTaken[0][0] = 1;
        List<CardChance> cards = board.changed()
                ? SecretOdds.readyByCard(game, viewer, rival, board.view, board.after)
                : SecretOdds.readyByCard(game, viewer, rival);
        for (CardChance card : cards) {
            double[][] next = new double[STATUS_SECRET_SCORES + 1][missing + 1];
            double actionChance = acts ? card.action() : 0;
            double none = 1 - card.status() - actionChance;
            for (int taken = 0; taken <= STATUS_SECRET_SCORES; taken++) {
                for (int action = 0; action <= missing; action++) {
                    double p = byStatusTaken[taken][action];
                    if (p == 0) continue;
                    next[taken][action] += p * none;
                    next[taken][cap(action + ONE_POINT, missing)] += p * actionChance;
                    next[Math.min(STATUS_SECRET_SCORES, taken + 1)][action] += p * card.status();
                }
            }
            byStatusTaken = next;
        }
        Grid grid = Grid.empty(missing);
        for (int taken = 0; taken <= STATUS_SECRET_SCORES; taken++) {
            for (int action = 0; action <= missing; action++) {
                grid.add(action, cap(taken, missing), byStatusTaken[taken][action]);
            }
        }
        return grid;
    }

    private static int cap(int points, int missing) {
        return Math.min(points, missing);
    }

    private static final class Grid {

        private final int missing;
        private final double[][] chances;

        private Grid(int missing) {
            this.missing = missing;
            this.chances = new double[missing + 1][missing + 1];
        }

        static Grid empty(int missing) {
            return new Grid(missing);
        }

        static Grid start(int missing) {
            return certain(missing, 0, 0);
        }

        static Grid certain(int missing, int action, int status) {
            return chance(missing, action, status, 1);
        }

        static Grid chance(int missing, int action, int status, double chance) {
            Grid grid = new Grid(missing);
            grid.add(0, 0, 1 - chance);
            grid.add(cap(action, missing), cap(status, missing), chance);
            return grid;
        }

        void add(int action, int status, double chance) {
            chances[action][status] += chance;
        }

        void addAll(double[][] layer) {
            for (int action = 0; action <= missing; action++) {
                for (int status = 0; status <= missing; status++) chances[action][status] += layer[action][status];
            }
        }

        Grid with(Grid other) {
            Grid combined = new Grid(missing);
            for (int a1 = 0; a1 <= missing; a1++) {
                for (int s1 = 0; s1 <= missing; s1++) {
                    double p1 = chances[a1][s1];
                    if (p1 == 0) continue;
                    for (int a2 = 0; a2 <= missing; a2++) {
                        for (int s2 = 0; s2 <= missing; s2++) {
                            double p2 = other.chances[a2][s2];
                            if (p2 == 0) continue;
                            combined.add(cap(a1 + a2, missing), cap(s1 + s2, missing), p1 * p2);
                        }
                    }
                }
            }
            return combined;
        }

        double actionAtLeast(int points) {
            double total = 0;
            for (int status = 0; status <= missing; status++) total += chances[cap(points, missing)][status];
            return total;
        }

        double totalAtLeast(int points, int late) {
            double total = 0;
            for (int action = 0; action <= missing; action++) {
                for (int status = 0; status <= missing; status++) {
                    if (action + status + late >= points) total += chances[action][status];
                }
            }
            return total;
        }
    }
}
