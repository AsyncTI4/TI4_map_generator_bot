package ti4.ai.tactical;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class PlanetTake {

    static final StrikeRules RULES = new StrikeRules(false, 0.5, true, true);

    public record Target(Tile tile, String planet, Player holder) {}

    public static Optional<String> contestedMecatol(Game game, Player taker) {
        return contestedMecatolTarget(game, taker).map(Target::planet);
    }

    public static double mecatolThisRound(Game game, Player decider, Player taker) {
        if (contestedMecatolTarget(game, taker).isEmpty()) return 0.0;
        boolean cannonRisk = cannonRisk(decider);
        return DecisionMemo.remember(
                () -> mecatol(game, taker, new IncomingForces(game), decider),
                "mecatol.take",
                taker,
                cannonRisk,
                game.getRound());
    }

    public static double mecatolThisRoundWithout(Game game, Player decider, Player taker, Tile stopped) {
        if (contestedMecatolTarget(game, taker).isEmpty()) return 0.0;
        boolean cannonRisk = cannonRisk(decider);
        return DecisionMemo.remember(
                () -> mecatol(
                        game, taker, new IncomingForces(game, new IncomingForces.Exclusion(taker, stopped)), decider),
                "mecatol.take.without",
                taker,
                stopped.getPosition(),
                cannonRisk,
                game.getRound());
    }

    static double chance(Game game, Player taker, Target target, IncomingForces incoming, Player decider) {
        try (Thinking.Span timing = Thinking.measure("planetTake.chance")) {
            IncomingForce force = incoming.of(taker, target.tile(), Horizon.IMMEDIATE);
            if (force.isEmpty() || !force.carriesGround()) return 0.0;
            if (target.holder() == null) return 1.0;
            Strike strike = new Strike(
                    target.tile(),
                    target.holder(),
                    List.of(),
                    List.of(),
                    force.ships(),
                    force.damaged(),
                    force.ground(),
                    Map.of(),
                    Map.of(),
                    RULES,
                    Map.of());
            StrikeEstimate estimate = StrikeEvaluator.estimate(game, taker, strike, decider);
            return estimate.spaceWin() * estimate.groundWins().getOrDefault(target.planet(), 0.0);
        }
    }

    private static double mecatol(Game game, Player taker, IncomingForces incoming, Player decider) {
        return contestedMecatolTarget(game, taker)
                .map(target -> chance(game, taker, target, incoming, decider))
                .orElse(0.0);
    }

    private static Optional<Target> contestedMecatolTarget(Game game, Player taker) {
        Tile tile = game.getMecatolTile();
        if (tile == null || taker.isPassed() || taker.controlsMecatol(true)) return Optional.empty();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (!game.mecatols().contains(planet.getName()) || BoardView.hasCustodians(planet)) continue;
            Player controller = BoardView.controller(game, planet.getName());
            return Optional.of(new Target(tile, planet.getName(), controller == null ? undefended(game) : controller));
        }
        return Optional.empty();
    }

    private static Player undefended(Game game) {
        return game.findNeutral().orElse(null);
    }

    private static boolean cannonRisk(Player decider) {
        return AiExperiments.enabled(decider, AiExperiments.Experiment.CANNON_COVER_RISK);
    }
}
