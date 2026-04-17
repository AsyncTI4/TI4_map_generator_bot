package ti4.service.combat;

import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

/**
 * Computes effective combat-round stats for units that are already known to be in a combat context.
 * This owns combat stat adjustments and profile construction, while
 * {@link CombatUnitSelectionHelper} decides which units participate and
 * {@link CombatRollService} handles validated roll execution.
 */
@UtilityClass
public class CombatStatsService {

    public static CombatRoundProfile getCombatRoundProfile(
            boolean participates, UnitModel unitModel, Player player, Tile activeSystem, @Nullable Player opponent) {
        EffectiveCombatStats effectiveCombatStats = getEffectiveCombatStats(unitModel, player, activeSystem, opponent);
        return new CombatRoundProfile(participates, effectiveCombatStats.diceCount(), effectiveCombatStats.hitsOn());
    }

    private static EffectiveCombatStats getEffectiveCombatStats(
            UnitModel unitModel, Player player, Tile activeSystem, @Nullable Player opponent) {
        int numRollsPerUnit = unitModel.getCombatDieCountForAbility(CombatRollType.combatround, player);
        int extraDice = 0;
        if (isEidolonLandwasterMech(unitModel, player)) extraDice++;
        if (isEchoOfAscensionFlagship(unitModel, player)) extraDice++;
        numRollsPerUnit += extraDice;
        if (isWinnuFlagship(unitModel) && numRollsPerUnit <= 0) {
            if (opponent != null) {
                numRollsPerUnit = ButtonHelper.checkNumberNonFighterShips(opponent, activeSystem);
            }
        }

        int toHit = unitModel.getCombatDieHitsOnForAbility(CombatRollType.combatround, player);
        int hitBonus = 0;
        if (isEidolonTerminusMech(unitModel, player)) hitBonus++;
        if (isEchoOfAscensionFlagship(unitModel, player)) hitBonus++;
        toHit = Math.max(1, toHit - hitBonus);
        return new EffectiveCombatStats(numRollsPerUnit, toHit);
    }

    private static boolean isEchoOfAscensionFlagship(UnitModel unitModel, Player player) {
        return unitModel.getUnitType() == UnitType.Flagship && player.ownsUnit("tf-echoofascension");
    }

    private static boolean isEidolonLandwasterMech(UnitModel unitModel, Player player) {
        return unitModel.getUnitType() == UnitType.Mech && player.ownsUnit("tf-eidolonlandwaster");
    }

    private static boolean isEidolonTerminusMech(UnitModel unitModel, Player player) {
        return unitModel.getUnitType() == UnitType.Mech && player.ownsUnit("tf-eidolonterminus");
    }

    private static boolean isWinnuFlagship(UnitModel unitModel) {
        return unitModel.getId() != null && unitModel.getId().contains("winnu_flagship");
    }

    /**
     * Effective combat-round state for a specific unit in a specific combat context.
     * This bundles the three values callers otherwise have to orchestrate separately:
     * whether the unit participates, how many dice it rolls, and what value it hits on.
     */
    public record CombatRoundProfile(boolean participates, int diceCount, int hitsOn) {}

    private record EffectiveCombatStats(int diceCount, int hitsOn) {}
}
