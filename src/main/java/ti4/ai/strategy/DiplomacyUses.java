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
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.PeaceAccords;
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
        double ready = readyValue(in);
        benefits.add(new Benefit(CardUse.DIPLOMACY_READY, ready));
        benefits.add(new Benefit(CardUse.DIPLOMACY_EXPEDITION, expeditionValue(in, ready)));
        peaceAccordsValue(in).ifPresent(benefits::add);
        return benefits;
    }

    static Optional<Benefit> bestProtection(PlanInputs in) {
        return protections(in).stream().max(Comparator.comparingDouble(Benefit::value));
    }

    static List<Benefit> protections(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        Tile mecatol = game.getMecatolTile();
        List<Tile> threatened = lockableTiles(game, seat).stream()
                .filter(tile -> tile != mecatol && in.isThreatened(tile))
                .toList();
        if (threatened.isEmpty()) return List.of();
        List<Benefit> benefits = new ArrayList<>();
        for (Tile tile : threatened) {
            double exposure = in.lockExposure(tile);
            if (exposure < CardValueScale.MIN_LOCK_EXPOSURE) continue;
            benefits.add(
                    new Benefit(CardUse.DIPLOMACY_PROTECT, protectionValue(in, tile, exposure), tile.getPosition()));
        }
        return benefits;
    }

    static Optional<String> mostValuableOpenSystem(Game game, Player seat) {
        return lockableTiles(game, seat).stream()
                .filter(tile -> tile != game.getMecatolTile())
                .filter(tile ->
                        game.getRealPlayers().stream().anyMatch(other -> other != seat && !tile.hasPlayerCC(other)))
                .max(Comparator.comparingDouble(tile -> ownedPlanetValue(seat, tile)))
                .map(Tile::getPosition);
    }

    private static double ownedPlanetValue(Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getPlanets().contains(planet.getName()))
                .mapToDouble(BoardView::planetValue)
                .sum();
    }

    static boolean isLockable(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null
                && tile != game.getMecatolTile()
                && lockableTiles(game, seat).contains(tile);
    }

    private static Set<Tile> lockableTiles(Game game, Player seat) {
        Set<Tile> tiles = new LinkedHashSet<>();
        seat.getPlanets().stream()
                .map(game::getTileFromPlanet)
                .filter(Objects::nonNull)
                .forEach(tiles::add);
        return tiles;
    }

    private static double protectionValue(PlanInputs in, Tile tile, double exposure) {
        Player seat = in.seat();
        double planets = tile.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getPlanets().contains(planet.getName()))
                .mapToDouble(BoardView::planetValue)
                .sum();
        double value = CardValueScale.PROTECT_SHARE * planets + exposure;
        return in.nearWin() ? value * CardValueScale.NEAR_WIN_MULTIPLIER : value;
    }

    private static Optional<Benefit> peaceAccordsValue(PlanInputs in) {
        double claim = PeaceAccords.bestValue(in.game(), in.seat());
        if (claim <= 0) return Optional.empty();
        return Optional.of(new Benefit(CardUse.DIPLOMACY_PEACE_ACCORDS, CardValueScale.PEACE_ACCORDS_SHARE * claim));
    }

    static Optional<DiplomacyUse.Choice> readyingChoice(Game game, Player seat) {
        Optional<DiplomacyUse.Choice> generic = DiplomacyUse.best(game, seat, seat.getExhaustedPlanets());
        double genericValue = generic.map(DiplomacyUses::value).orElse(0.0);
        return ExpeditionUnlock.afterReadying(game, seat, seat.getExhaustedPlanets())
                .filter(readying -> readying.gain() > genericValue)
                .map(readying -> DiplomacyUse.readying(game, seat, readying.planets()))
                .or(() -> generic);
    }

    private static double value(DiplomacyUse.Choice choice) {
        return choice.unlocksScore()
                ? CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT
                : CardValueScale.READY_SHARE * choice.use();
    }

    private static double expeditionValue(PlanInputs in, double readyValue) {
        Game game = in.game();
        Player seat = in.seat();
        double unlocked = in.moment() == PlanMoment.ACTION
                ? ExpeditionUnlock.afterReadying(game, seat, seat.getExhaustedPlanets())
                        .map(ExpeditionUnlock.Readying::gain)
                        .orElse(0.0)
                : ExpeditionUnlock.refundOfFirstClaim(game, seat);
        return Math.max(0, unlocked - readyValue);
    }

    private static double readyValue(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        if (in.moment() == PlanMoment.ACTION) {
            return DiplomacyUse.best(game, seat, seat.getExhaustedPlanets())
                    .map(DiplomacyUses::value)
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

    static boolean revealsSpendObjective(Game game, Player seat) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .anyMatch(id -> ObjectiveCatalog.isSpend(id) && !ObjectivePolicy.hasScored(game, seat, id));
    }
}
