package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
class ConstructionUses {

    private static final int ONE_STRUCTURE = 1;

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        List<Benefit> structures = structures(in);
        if (CardVariant.of(in.game(), initiative) != CardVariant.THUNDERS_EDGE) return structures;
        Optional<Benefit> production = bestProduction(in);
        if (production.isEmpty()) return structures;
        List<Benefit> produce = new ArrayList<>();
        produce.add(production.get());
        structure(in, StructurePolicy.next(in.game(), in.seat()), "").ifPresent(produce::add);
        if (StructurePolicy.structureShortfall(in.game(), in.seat()) <= ONE_STRUCTURE) produce.add(objective());
        return total(produce) > total(structures) ? produce : structures;
    }

    static Optional<Benefit> bestProduction(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        if (game.isFowMode()) return Optional.empty();
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock).stream()
                .filter(Objects::nonNull)
                .filter(tile -> !BoardView.hasEnemyShips(game, seat, tile))
                .map(tile -> productionAt(in, tile))
                .filter(benefit -> benefit.value() > 0)
                .max(Comparator.comparingDouble(Benefit::value));
    }

    static boolean canProduceAt(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null
                && BoardView.countInTile(tile, seat, UnitType.Spacedock) > 0
                && !BoardView.hasEnemyShips(game, seat, tile);
    }

    private static Benefit productionAt(PlanInputs in, Tile tile) {
        double built = ProductionPlanner.plan(in.game(), in.seat(), tile).value();
        double moveOut = TacticalPlanner.bestScore(movingOutOf(in.ranked(), tile.getPosition()));
        double value = CardValueScale.PRODUCTION_SHARE * built + CardValueScale.BUILD_THEN_MOVE_SHARE * moveOut;
        return new Benefit(CardUse.CONSTRUCTION_PRODUCE, built > 0 ? value : 0, tile.getPosition());
    }

    static List<TacticalPlan> movingOutOf(List<TacticalPlan> ranked, String position) {
        return ranked.stream()
                .filter(plan ->
                        plan.moves().stream().anyMatch(move -> move.origin().equals(position)))
                .toList();
    }

    private static List<Benefit> structures(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        Optional<String> first = StructurePolicy.next(game, seat);
        if (first.isEmpty()) return List.of();
        List<Benefit> benefits = new ArrayList<>();
        double pds = 0;
        if (StructurePolicy.SPACE_DOCK.equals(first.get())) {
            structure(in, first, "").ifPresent(benefits::add);
        } else {
            pds += CardValueScale.PDS;
        }
        if (StructurePolicy.pds(game, seat).isPresent()) pds += CardValueScale.PDS;
        Optional<String> threatened = threatenedSeatTile(in);
        if (pds > 0 && threatened.isPresent()) pds += CardValueScale.THREATENED_PDS;
        benefits.add(new Benefit(CardUse.CONSTRUCTION_PDS, pds, threatened.orElse("")));
        if (StructurePolicy.nearsStructureObjective(game, seat)) benefits.add(objective());
        return benefits;
    }

    private static Optional<Benefit> structure(PlanInputs in, Optional<String> unit, String target) {
        if (unit.isEmpty()) return Optional.empty();
        if (!StructurePolicy.SPACE_DOCK.equals(unit.get())) {
            return Optional.of(new Benefit(CardUse.CONSTRUCTION_PDS, CardValueScale.PDS, target));
        }
        double dock = in.round() <= CardValueScale.LAST_EARLY_DOCK_ROUND
                ? CardValueScale.DOCK_EARLY
                : CardValueScale.DOCK_LATE;
        String planet = StructurePolicy.planetFor(
                        in.game(),
                        in.seat(),
                        StructurePolicy.SPACE_DOCK,
                        in.seat().getPlanets())
                .orElse("");
        return Optional.of(new Benefit(CardUse.CONSTRUCTION_DOCK, dock, planet));
    }

    private static Benefit objective() {
        return new Benefit(
                CardUse.CONSTRUCTION_OBJECTIVE,
                CardValueScale.STRUCTURE_OBJECTIVE_SHARE * CardValueScale.VICTORY_POINT);
    }

    private static Optional<String> threatenedSeatTile(PlanInputs in) {
        Game game = in.game();
        return in.seat().getPlanets().stream()
                .map(game::getTileFromPlanet)
                .filter(Objects::nonNull)
                .filter(in::isThreatened)
                .map(Tile::getPosition)
                .findFirst();
    }

    private static double total(List<Benefit> benefits) {
        return benefits.stream().mapToDouble(Benefit::value).sum();
    }
}
