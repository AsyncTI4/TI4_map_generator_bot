package ti4.service.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.UnitModel;

/**
 * Selects which units participate in a combat round for a given tile, holder, and player.
 * This owns combat-round participant collection and eligibility rules, while
 * {@link CombatStatsService} computes the effective combat stats for those units and
 * {@link CombatRollService} handles validated roll execution.
 */
@UtilityClass
public class CombatUnitSelectionHelper {

    public static Map<UnitModel, Integer> collectCombatRoundUnits(Tile tile, UnitHolder unitHolder, Player player) {
        CombatSelectionContext context = CombatSelectionContext.forCombatHolder(tile, unitHolder, player);
        if (context.isSpaceCombat()) {
            return selectSpaceUnits(context);
        }
        return selectGroundUnits(context.unitsOnCombatHolder());
    }

    private static Map<UnitModel, Integer> selectSpaceUnits(CombatSelectionContext context) {
        Map<UnitModel, Integer> selectedUnits = selectStandardSpaceUnits(context.unitsOnCombatHolder());
        // Nekro flagship lets its owner's ground forces join the space combat from elsewhere in the system.
        if (hasNekroFlagship(context)) {
            selectedUnits = includeGroundForcesFromSystem(context);
            // Purple TF mechs / Naaz Voltron let those mechs join the space combat from planets in the system.
        } else if (hasPurpleTFMech(context.player())) {
            selectedUnits = includeMechsFromPlanets(context);
        }
        return applySpaceRestrictions(context, selectedUnits);
    }

    private static Map<UnitModel, Integer> applySpaceRestrictions(
            CombatSelectionContext context, Map<UnitModel, Integer> selectedUnits) {
        // In Cosmic Phenomena, asteroid fields keep fighters out of space combat unless the player has FF2.
        if (context.player().getGame().isCosmicPhenomenaeMode()
                && context.tile().isAsteroidField()
                && !context.player().hasFF2Tech()) {
            return filterUnits(selectedUnits, unit -> unit.getUnitType() != UnitType.Fighter);
        }
        return selectedUnits;
    }

    private static boolean hasNekroFlagship(CombatSelectionContext context) {
        return context.unitsByAsyncIdOnCombatHolder().containsKey("fs")
                && (context.player().hasUnit("nekro_flagship")
                        || context.player().hasUnit("sigma_nekro_flagship_2"));
    }

    private static Map<UnitModel, Integer> includeGroundForcesFromSystem(CombatSelectionContext context) {
        return collectEligibleUnitsFromSystem(
                context.tile(), context.player(), unit -> unit.getIsGroundForce() || unit.getIsShip());
    }

    private static boolean hasPurpleTFMech(Player player) {
        return player.hasUnit("purpletf_mech") || player.hasUnit("naaz_voltron");
    }

    private static Map<UnitModel, Integer> includeMechsFromPlanets(CombatSelectionContext context) {
        return collectEligibleUnitsFromSystem(
                context.tile(), context.player(), unit -> unit.getUnitType() == UnitType.Mech || unit.getIsShip());
    }

    private static Map<UnitModel, Integer> selectStandardSpaceUnits(Map<UnitModel, Integer> unitsOnCombatHolder) {
        return filterUnits(unitsOnCombatHolder, UnitModel::getIsShip);
    }

    private static Map<UnitModel, Integer> selectGroundUnits(Map<UnitModel, Integer> unitsOnCombatHolder) {
        return filterUnits(unitsOnCombatHolder, unit -> unit.getIsGroundForce() || unit.getIsShip());
    }

    private static Map<UnitModel, Integer> collectEligibleUnitsFromSystem(
            Tile tile, Player player, Predicate<UnitModel> eligibilityCheck) {
        Map<UnitModel, Integer> mergedUnits = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            addEligibleUnitsOnHolder(mergedUnits, unitHolder, player, eligibilityCheck);
        }
        return mergedUnits;
    }

    private static void addEligibleUnitsOnHolder(
            Map<UnitModel, Integer> mergedUnits,
            UnitHolder unitHolder,
            Player player,
            Predicate<UnitModel> eligibilityCheck) {
        Map<UnitModel, Integer> unitsOnHolder = collectUnitsOnHolder(unitHolder, player);
        for (Map.Entry<UnitModel, Integer> entry : unitsOnHolder.entrySet()) {
            UnitModel unit = entry.getKey();
            if (unit == null || !eligibilityCheck.test(unit)) continue;
            mergedUnits.merge(unit, entry.getValue(), Integer::sum);
        }
    }

    private static Map<UnitModel, Integer> collectUnitsOnHolder(UnitHolder unitHolder, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        return unitsByAsyncId.entrySet().stream()
                .map(entry -> new ImmutablePair<>(
                        player.getPriorityUnitByAsyncID(entry.getKey(), unitHolder), entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private static Map<UnitModel, Integer> filterUnits(
            Map<UnitModel, Integer> unitsInCombat, Predicate<UnitModel> eligibilityCheck) {
        return new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && eligibilityCheck.test(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Carries the precomputed unit data needed to apply combat participation rules for one player on one holder.
     */
    private record CombatSelectionContext(
            Tile tile,
            UnitHolder unitHolder,
            Player player,
            Map<String, Integer> unitsByAsyncIdOnCombatHolder,
            Map<UnitModel, Integer> unitsOnCombatHolder) {

        private static CombatSelectionContext forCombatHolder(Tile tile, UnitHolder unitHolder, Player player) {
            String colorID = Mapper.getColorID(player.getColor());
            Map<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
            Map<UnitModel, Integer> unitsOnCombatHolder = unitsByAsyncId.entrySet().stream()
                    .map(entry -> new ImmutablePair<>(
                            player.getPriorityUnitByAsyncID(entry.getKey(), unitHolder), entry.getValue()))
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
            return new CombatSelectionContext(tile, unitHolder, player, unitsByAsyncId, unitsOnCombatHolder);
        }

        private boolean isSpaceCombat() {
            return Constants.SPACE.equals(unitHolder.getName());
        }
    }
}
