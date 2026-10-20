package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.strategy.MecatolCash;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class FreedPlays {

    private static final int CANDIDATES = 8;
    private static final int SECRETS_PER_ROUND = 1;
    private static final int CUSTODIANS_POINT = 1;
    private static final int OBJECTIVE_DENIAL_POINT = 1;

    public record Gain(double points, boolean winsGame, boolean stopsLeader) {

        public static final Gain NONE = new Gain(0, false, false);
    }

    private record Play(double points, boolean winsGame, boolean stopsLeader, double score) {

        static final Play NOTHING = new Play(0, false, false, 0);
    }

    private record Denial(double points, boolean stopsLeader) {

        static final Denial NONE = new Denial(0, false);
    }

    public static Gain gain(Game game, Player seat) {
        Appraisal appraisal = new Appraisal(game, seat);
        Play freed = appraisal.best(TacticalPlanner.rankedFreeingEveryToken(game, seat));
        Play blocked = appraisal.best(TacticalPlanner.ranked(game, seat));
        return new Gain(
                Math.max(0, freed.points() - blocked.points()),
                freed.winsGame() && !blocked.winsGame(),
                freed.stopsLeader() && !blocked.stopsLeader());
    }

    private static final class Appraisal {
        private final Game game;
        private final Player seat;
        private final ObjectiveValue objectives;
        private final DefenseOutlook defense;
        private final OffenseOutlook offense;

        Appraisal(Game game, Player seat) {
            this.game = game;
            this.seat = seat;
            this.objectives = new ObjectiveValue(game, seat);
            this.defense = new DefenseOutlook(game, seat);
            this.offense = new OffenseOutlook(game, seat, defense);
        }

        Play best(List<TacticalPlan> plans) {
            return plans.stream()
                    .limit(CANDIDATES)
                    .map(this::appraise)
                    .max(Comparator.comparing(Play::winsGame)
                            .thenComparing(Play::stopsLeader)
                            .thenComparingDouble(Play::points)
                            .thenComparingDouble(Play::score))
                    .orElse(Play.NOTHING);
        }

        private Play appraise(TacticalPlan plan) {
            int own = Math.min(completedObjectives(plan), ScoringReserve.scoringChances(game, seat) + SECRETS_PER_ROUND)
                    + mecatolPoints(plan);
            Denial denial = denial(plan);
            boolean wins = own > 0 && seat.getTotalVictoryPoints() + own >= game.getVp();
            return new Play(own + denial.points(), wins, denial.stopsLeader(), plan.score());
        }

        private int completedObjectives(TacticalPlan plan) {
            List<Footprint.Move> moves = plan.moves().stream()
                    .map(move -> new Footprint.Move(move.origin(), move.holder(), move.type(), move.count()))
                    .toList();
            return objectives.completedPoints(objectives
                    .before()
                    .after(plan.target(), moves, plan.landings().keySet()));
        }

        private int mecatolPoints(TacticalPlan plan) {
            if (seat.controlsMecatol(true)) return 0;
            Optional<Planet> mecatol = landedMecatol(plan);
            if (mecatol.isEmpty()) return 0;
            int custodians = BoardView.hasCustodians(mecatol.get()) ? CUSTODIANS_POINT : 0;
            return custodians + MecatolCash.points(game, seat);
        }

        private Optional<Planet> landedMecatol(TacticalPlan plan) {
            return plan.landings().keySet().stream()
                    .filter(game.mecatols()::contains)
                    .map(game::getUnitHolderFromPlanet)
                    .filter(Planet.class::isInstance)
                    .map(Planet.class::cast)
                    .findFirst();
        }

        private Denial denial(TacticalPlan plan) {
            Tile tile = game.getTileByPosition(plan.target());
            if (tile == null || plan.kind() != Kind.ATTACK || plan.landings().isEmpty()) return Denial.NONE;
            Player opponent = TacticalPlanner.singleOpponent(game, seat, tile);
            Standings standings = defense.standings();
            if (opponent == null || standings.winThreat(opponent) <= 0) return Denial.NONE;
            double points = tile == opponent.getHomeSystemTile()
                    ? standings.publicPoints(opponent)
                    : objectivePlanetsTaken(plan, opponent);
            points += mecatolDenied(plan, opponent, standings);
            double capped = Math.min(points, standings.publicScores(opponent) + OBJECTIVE_DENIAL_POINT);
            double projected = standings.projected(opponent);
            boolean stops = projected >= game.getVp() && projected - capped < game.getVp();
            return new Denial(capped, stops);
        }

        private double objectivePlanetsTaken(TacticalPlan plan, Player opponent) {
            return plan.landings().keySet().stream()
                            .map(game::getUnitHolderFromPlanet)
                            .filter(Planet.class::isInstance)
                            .map(Planet.class::cast)
                            .filter(planet -> BoardView.controller(game, planet.getName()) == opponent)
                            .filter(planet -> offense.deniesObjectives(opponent, planet))
                            .count()
                    * (double) OBJECTIVE_DENIAL_POINT;
        }

        private double mecatolDenied(TacticalPlan plan, Player opponent, Standings standings) {
            boolean takesTheirMecatol = landedMecatol(plan)
                    .filter(planet -> BoardView.controller(game, planet.getName()) == opponent)
                    .isPresent();
            return takesTheirMecatol ? standings.imperialPoint(opponent) : 0;
        }
    }
}
