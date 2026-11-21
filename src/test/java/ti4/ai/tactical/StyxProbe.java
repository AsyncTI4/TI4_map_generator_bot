package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FracturePrize;
import ti4.ai.eval.MovementGraph;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

/**
 * Self-play diagnostics for Styx (-Dai.selfplay.styxprobe=true): one line per call with the seat's best strike on Styx
 * and the gate that rejects it, the staging plan toward Styx and its gates, the fleet that could reach Styx within one
 * to four tactical actions, and whether the seat's docks are near an ingress (the ProductionPlanner warship factor).
 */
public final class StyxProbe {

    public static final String PROPERTY = "ai.selfplay.styxprobe";
    private static final int PRODUCTION_RANGE = 2;

    private StyxProbe() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static Optional<String> report(Game game, Player seat) {
        Optional<Tile> styxTile = FracturePrize.tiles(game).stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream().anyMatch(FracturePrize::isStyx))
                .findFirst();
        if (styxTile.isEmpty()) return Optional.empty();
        Tile styx = styxTile.get();
        Planet styxPlanet = styx.getPlanetUnitHolders().stream()
                .filter(FracturePrize::isStyx)
                .findFirst()
                .orElseThrow();
        if (seat.getPlanets().contains(styxPlanet.getName())) return Optional.of(prefix(game, seat) + " holds=1");
        TacticalPlanner.Context context = new TacticalPlanner.Context(game, seat);
        StringBuilder line = new StringBuilder(prefix(game, seat));
        Player holder = BoardView.controller(game, styxPlanet.getName());
        line.append(" holder=").append(holder == null ? "none" : holder.getFaction());
        reachLine(context, game, seat, styx, line);
        productionLine(game, seat, line);
        Player opponent = TacticalPlanner.singleOpponent(game, seat, styx);
        line.append(" opp=")
                .append(opponent == null ? "null" : opponent.isNeutral() ? "neutral" : opponent.getFaction());
        if (opponent != null) {
            strikeLine(context, styx, opponent, line);
            stageLine(context, game, seat, styx, opponent, line);
        }
        List<TacticalPlan> ranked =
                TacticalPlanner.candidates(context, tile -> TacticalPlanner.activatable(game, seat, tile));
        if (ranked.isEmpty()) {
            line.append(" top=none");
        } else {
            TacticalPlan top = ranked.getFirst();
            line.append(" top=")
                    .append(top.kind())
                    .append('@')
                    .append(top.target())
                    .append(':')
                    .append(fmt(top.score()));
            ranked.stream()
                    .filter(plan -> plan.target().equals(styx.getPosition()) || isStyxStage(game, plan))
                    .findFirst()
                    .ifPresent(plan -> line.append(" styxRank=")
                            .append(ranked.indexOf(plan))
                            .append(':')
                            .append(plan.kind())
                            .append(':')
                            .append(fmt(plan.score())));
        }
        return Optional.of(line.toString());
    }

    private static boolean isStyxStage(Game game, TacticalPlan plan) {
        if (plan.kind() != TacticalPlan.Kind.STAGE) return false;
        Tile target = game.getTileByPosition(plan.target());
        return target != null && target.isFracture();
    }

    private static String prefix(Game game, Player seat) {
        return "STYXPROBE game=" + game.getName() + " r=" + game.getRound() + " seat=" + seat.getFaction() + " vp="
                + seat.getTotalVictoryPoints();
    }

    private static void reachLine(
            TacticalPlanner.Context context, Game game, Player seat, Tile styx, StringBuilder line) {
        double[] within = new double[5];
        for (Tile origin : context.origins()) {
            for (UnitType type : BoardView.MOVING_SHIPS) {
                int count = BoardView.count(BoardView.space(origin), seat, type);
                if (count == 0) continue;
                int move = BoardView.moveValueWithGravityDrive(seat, type);
                for (int actions = 1; actions <= 4; actions++) {
                    if (context.reach(origin, actions * move).containsKey(styx.getPosition())) {
                        within[actions] += count * TacticalPlanner.cost(seat, type);
                    }
                }
            }
        }
        line.append(" reach1-4=")
                .append(fmt(within[1]))
                .append('/')
                .append(fmt(within[2]))
                .append('/')
                .append(fmt(within[3]))
                .append('/')
                .append(fmt(within[4]));
        int dreads = 0;
        double fleet = 0;
        for (Tile tile : game.getTileMap().values()) {
            dreads += BoardView.count(BoardView.space(tile), seat, UnitType.Dreadnought);
            for (UnitType type : BoardView.MOVING_SHIPS) {
                fleet += BoardView.count(BoardView.space(tile), seat, type) * TacticalPlanner.cost(seat, type);
            }
        }
        line.append(" dn=").append(dreads).append(" fleet=").append(fmt(fleet));
        double grouped = 0;
        double total = 0;
        for (Tile origin : context.origins()) {
            for (UnitType type : BoardView.MOVING_SHIPS) {
                total += BoardView.count(BoardView.space(origin), seat, type) * TacticalPlanner.cost(seat, type);
            }
            for (var move : context.stageGroup(origin))
                grouped += move.count() * TacticalPlanner.cost(seat, move.type());
        }
        line.append(" free=").append(fmt(total)).append(" heldBack=").append(fmt(total - grouped));
    }

    private static void productionLine(Game game, Player seat, StringBuilder line) {
        int nearest = Integer.MAX_VALUE;
        for (Tile tile : game.getTileMap().values()) {
            boolean dock = tile.getPlanetUnitHolders().stream()
                    .anyMatch(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0);
            if (!dock) continue;
            for (var entry :
                    MovementGraph.reach(game, seat, tile.getPosition(), 4).entrySet()) {
                Tile reached = game.getTileByPosition(entry.getKey());
                if (reached != null && FracturePrize.hasIngress(reached)) nearest = Math.min(nearest, entry.getValue());
            }
        }
        line.append(" dockToIngress=")
                .append(nearest == Integer.MAX_VALUE ? "-" : nearest)
                .append(" prodFactor=")
                .append(nearest <= PRODUCTION_RANGE ? FracturePrize.interest(game, seat) : 0);
    }

    private static void strikeLine(TacticalPlanner.Context context, Tile styx, Player opponent, StringBuilder line) {
        List<TacticalPlanner.Candidate> pool = TacticalPlanner.candidatePool(context, styx, opponent);
        if (pool.isEmpty()) {
            line.append(" strike=none");
            return;
        }
        TacticalPlanner.Candidate best = pool.stream()
                .max(Comparator.comparingDouble(found -> found.quick().score()))
                .orElseThrow();
        StrikeValue value = context.offense().evaluate(best.strike(), best.estimate(), best.terms(), true);
        double groundWin = best.estimate().groundWins().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
        String gate = best.estimate().spaceWin() < best.strike().rules().landMinWin()
                ? "spaceWin"
                : value.score() < TacticalPlanner.ATTACK_MIN_SCORE ? "score" : "ok";
        line.append(" strike=")
                .append(gate)
                .append(" sWin=")
                .append(fmt(best.estimate().spaceWin()))
                .append(" gWin=")
                .append(fmt(groundWin))
                .append(" val=")
                .append(fmt(value.value()))
                .append(" loss=")
                .append(fmt(value.losses()))
                .append(" ctr=")
                .append(fmt(value.counter()))
                .append(" sc=")
                .append(fmt(value.score()))
                .append(" atk=")
                .append(TacticalPlanner.fullPlan(context, styx, best).isPresent());
    }

    private static void stageLine(
            TacticalPlanner.Context context, Game game, Player seat, Tile styx, Player opponent, StringBuilder line) {
        if (!TacticalPlanner.worthStagingFor(context, styx, opponent)) {
            line.append(" stage=notWorth");
            return;
        }
        double feasibility = TacticalPlanner.stageFeasibility(context, styx, opponent);
        line.append(" feas=")
                .append(fmt(feasibility))
                .append(" prize=")
                .append(fmt(TacticalPlanner.stagePrize(context, styx, opponent)));
        if (feasibility < 0.5) {
            line.append(" stage=feas");
            return;
        }
        if (context.attackPlan(styx).isPresent()) {
            line.append(" stage=attackExists");
            return;
        }
        List<TacticalPlanner.Gathering> gatherings = new ArrayList<>();
        for (Tile staging : game.getTileMap().values()) {
            if (staging == styx || staging == seat.getHomeSystemTile()) continue;
            if (!TacticalPlanner.activatable(game, seat, staging) || !MovementGraph.canEnter(game, seat, staging)) {
                continue;
            }
            if (BoardView.hasEnemyShips(game, seat, staging)) continue;
            TacticalPlanner.gather(context, styx, staging, TacticalPlanner.stageLead(opponent))
                    .ifPresent(gatherings::add);
        }
        if (gatherings.isEmpty()) {
            line.append(" stage=noGathering");
            return;
        }
        double bestFuture = Double.NEGATIVE_INFINITY;
        double bestScore = Double.NEGATIVE_INFINITY;
        String bestAt = "";
        String parts = "";
        for (TacticalPlanner.Gathering gathering : gatherings) {
            double future = TacticalPlanner.futureScore(
                    context, styx, opponent, gathering.staging(), gathering.ships(), gathering.cargo());
            if (future > bestFuture) {
                bestFuture = future;
                bestAt = gathering.staging().getPosition();
            }
            Optional<TacticalPlan> plan = TacticalPlanner.stagePlan(
                    context,
                    styx,
                    opponent,
                    gathering.withCounter(TacticalPlanner.counterAt(context, gathering, opponent)));
            if (plan.isPresent() && plan.get().score() > bestScore) {
                bestScore = plan.get().score();
                double counter = TacticalPlanner.counterAt(context, gathering, opponent);
                double leaving = gathering.contributions().stream()
                        .mapToDouble(found -> context.defense().leaveCost(found.origin(), found.moves()))
                        .sum();
                parts = "fut=" + fmt(future) + ",ctr=" + fmt(counter) + ",leave=" + fmt(leaving) + "@"
                        + gathering.staging().getPosition();
            }
        }
        TacticalPlanner.Gathering biggest = gatherings.stream()
                .max(Comparator.comparingDouble(TacticalPlanner.Gathering::cost))
                .orElseThrow();
        line.append(" biggest=").append(biggest.staging().getPosition()).append(':');
        biggest.ships()
                .forEach(move -> line.append(move.count()).append(move.type().getValue()));
        line.append('+');
        biggest.cargo()
                .forEach(move -> line.append(move.count()).append(move.type().getValue()));
        line.append(" fleetCC=").append(ti4.ai.eval.FleetLimit.ships(seat));
        line.append(" gathers=")
                .append(gatherings.size())
                .append(" future=")
                .append(fmt(bestFuture))
                .append('@')
                .append(bestAt)
                .append(" stage=")
                .append(bestFuture < TacticalPlanner.ATTACK_MIN_SCORE ? "future" : "ok")
                .append(" stageSc=")
                .append(bestScore == Double.NEGATIVE_INFINITY ? "-" : fmt(bestScore))
                .append(" parts=")
                .append(parts);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
