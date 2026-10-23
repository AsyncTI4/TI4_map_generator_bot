package ti4.ai.tactical;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FracturePrize;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

final class FractureScreen {

    private static final double SCREEN_SHARE = 0.5;
    private static final double PARTNER_SHARE = 0.5;
    private static final double MIN_THREAT = 0.3;
    private static final StrikeRules RULES = new StrikeRules(false, 0.5, true, true);

    private final Game game;
    private final Player seat;
    private final Standings standings;
    private final Map<String, Double> chances = new HashMap<>();
    private Map<String, Double> values;

    FractureScreen(Game game, Player seat, Standings standings) {
        this.game = game;
        this.seat = seat;
        this.standings = standings;
    }

    double valueOf(Tile tile) {
        if (values == null) values = compute();
        return values.getOrDefault(tile.getPosition(), 0.0);
    }

    private Map<String, Double> compute() {
        Map<String, Double> computed = new HashMap<>();
        Optional<Tile> styx = contestedStyx();
        if (styx.isEmpty()) return computed;
        Player holder = holderOf(styx.get());
        List<Tile> candidates = screenTiles(styx.get());
        Set<String> own = candidates.stream()
                .filter(tile -> BoardView.hasOwnShips(seat, tile))
                .map(Tile::getPosition)
                .collect(HashSet::new, Set::add, Set::addAll);
        for (Player rival : game.getRealPlayers()) {
            if (rival == seat || rival == holder) continue;
            Threat threat = new Threat(rival, holder, styx.get());
            double before = threat.chance(own);
            if (before < MIN_THREAT) continue;
            double worth = SCREEN_SHARE * standings.denialWeight(rival) * ObjectiveValue.VICTORY_POINT_VALUE;
            for (Tile candidate : candidates) {
                if (own.contains(candidate.getPosition())) continue;
                double cut = worth * threat.cutBy(own, candidate, candidates);
                if (cut > 0) computed.merge(candidate.getPosition(), cut, Double::sum);
            }
        }
        return computed;
    }

    private final class Threat {

        private final Player rival;
        private final Player defender;
        private final Tile styx;

        Threat(Player rival, Player defender, Tile styx) {
            this.rival = rival;
            this.defender = defender;
            this.styx = styx;
        }

        double cutBy(Set<String> own, Tile candidate, List<Tile> candidates) {
            double before = chance(own);
            double alone = chance(with(own, candidate));
            double partnered = 0;
            for (Tile partner : candidates) {
                if (partner == candidate || own.contains(partner.getPosition())) continue;
                double sealed = chance(with(with(own, candidate), partner));
                boolean needsBoth = sealed < alone && sealed < chance(with(own, partner));
                if (needsBoth) partnered = Math.max(partnered, alone - sealed);
            }
            return (before - alone) + PARTNER_SHARE * partnered;
        }

        double chance(Set<String> screened) {
            String key = rival.getFaction() + "|" + new TreeSet<>(screened);
            return chances.computeIfAbsent(key, ignored -> computeChance(screened));
        }

        private double computeChance(Set<String> screened) {
            IncomingForce force = IncomingForces.screened(game, screened).of(rival, styx, Horizon.NEXT_ROUND);
            if (force.isEmpty()) return 0;
            Strike strike = new Strike(
                    styx,
                    defender,
                    List.of(),
                    List.of(),
                    force.ships(),
                    force.damaged(),
                    force.ground(),
                    Map.of(),
                    Map.of(),
                    RULES,
                    Map.of());
            StrikeEstimate estimate = StrikeEvaluator.estimate(game, rival, strike);
            return estimate.spaceWin() * estimate.groundWins().getOrDefault(FracturePrize.STYX, 0.0);
        }
    }

    private static Set<String> with(Set<String> screened, Tile tile) {
        Set<String> extended = new HashSet<>(screened);
        extended.add(tile.getPosition());
        return extended;
    }

    private Optional<Tile> contestedStyx() {
        return FracturePrize.tiles(game).stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream().anyMatch(FracturePrize::isStyx))
                .filter(tile -> !FracturePrize.holdsStyx(tile, seat))
                .findFirst();
    }

    private Player holderOf(Tile styx) {
        for (Planet planet : styx.getPlanetUnitHolders()) {
            Player controller = BoardView.controller(game, planet.getName());
            if (FracturePrize.isStyx(planet) && controller != null) return controller;
        }
        return game.getNeutral();
    }

    // TODO: ingress systems on a rival's path are not screened, and a screen is planned one ship, one system per action
    private List<Tile> screenTiles(Tile styx) {
        return game.getTileMap().values().stream()
                .filter(Tile::isFracture)
                .filter(tile -> tile != styx && tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> !BoardView.hasEnemyShips(game, seat, tile))
                .toList();
    }
}
