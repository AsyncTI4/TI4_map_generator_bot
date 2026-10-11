package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class DiplomacyUses {

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        if (CardVariant.of(in.game(), initiative) == CardVariant.BASE_GAME) return List.of();
        List<Benefit> benefits = new ArrayList<>();
        bestProtection(in).ifPresent(benefits::add);
        benefits.add(new Benefit(CardUse.DIPLOMACY_READY, readyValue(in)));
        return benefits;
    }

    static Optional<Benefit> bestProtection(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        Tile mecatol = game.getMecatolTile();
        return lockableTiles(game, seat).stream()
                .filter(tile -> tile != mecatol && in.isThreatened(tile))
                .map(tile -> new Benefit(CardUse.DIPLOMACY_PROTECT, protectionValue(in, tile), tile.getPosition()))
                .max(Comparator.comparingDouble(Benefit::value));
    }

    static boolean isLockable(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null && tile != game.getMecatolTile() && lockableTiles(game, seat).contains(tile);
    }

    private static Set<Tile> lockableTiles(Game game, Player seat) {
        Set<Tile> tiles = new LinkedHashSet<>();
        seat.getPlanets().stream()
                .map(game::getTileFromPlanet)
                .filter(Objects::nonNull)
                .forEach(tiles::add);
        return tiles;
    }

    private static double protectionValue(PlanInputs in, Tile tile) {
        Player seat = in.seat();
        double planets = tile.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getPlanets().contains(planet.getName()))
                .mapToDouble(BoardView::planetValue)
                .sum();
        double value = CardValueScale.PROTECT_SHARE * planets;
        if (tile == seat.getHomeSystemTile()) value += CardValueScale.HOME_DEFENSE;
        return in.nearWin() ? value * CardValueScale.NEAR_WIN_MULTIPLIER : value;
    }

    private static double readyValue(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        if (in.moment() == PlanMoment.ACTION) {
            return DiplomacyUse.best(game, seat, seat.getExhaustedPlanets())
                    .map(choice -> choice.unlocksScore()
                            ? CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT
                            : CardValueScale.READY_SHARE * choice.use())
                    .orElse(0.0);
        }
        int bestTwo = seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(Objects::nonNull)
                .mapToInt(DiplomacyUses::higherValue)
                .boxed()
                .sorted(Comparator.reverseOrder())
                .limit(DiplomacyUse.PLANETS_TO_READY)
                .mapToInt(Integer::intValue)
                .sum();
        double value = CardValueScale.READY_SHARE * Math.min(bestTwo, CardValueScale.READY_FORECAST_CAP);
        return revealsSpendObjective(game, seat) ? value * CardValueScale.READY_SPEND_BONUS : value;
    }

    private static int higherValue(Planet planet) {
        return Math.max(planet.getResources(), planet.getInfluence());
    }

    private static boolean revealsSpendObjective(Game game, Player seat) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .anyMatch(id -> ObjectiveCatalog.isSpend(id) && !ObjectivePolicy.hasScored(game, seat, id));
    }
}
