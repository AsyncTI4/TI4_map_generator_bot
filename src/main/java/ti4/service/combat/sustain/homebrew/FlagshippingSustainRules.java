package ti4.service.combat.sustain.homebrew;

import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.UnitModel;
import ti4.service.combat.sustain.SustainCatalog;
import ti4.service.combat.sustain.SustainContext;
import ti4.service.combat.sustain.SustainRule;

public final class FlagshippingSustainRules implements SustainCatalog {

    public static final SustainCatalog CATALOG = new FlagshippingSustainRules();

    private static final List<SustainRule> RULES = List.of(
            SustainRule.suppressor("sigma_mentak_flagship_2", FlagshippingSustainRules::shipInOrNextToFourthMoonTwo),
            SustainRule.grant("sigma_nekro_flagship_1", FlagshippingSustainRules::groundForceInSpaceWithAlastorOne),
            SustainRule.grant("sigma_nekro_flagship_2", FlagshippingSustainRules::groundForceWithAlastorTwo));

    @Override
    public List<SustainRule> rules() {
        return RULES;
    }

    private static boolean shipInOrNextToFourthMoonTwo(SustainContext context, UnitModel unit) {
        Player mentak = Helper.getPlayerFromUnit(context.game(), "sigma_mentak_flagship_2");
        return unit.getIsShip()
                && context.inSpace()
                && mentak != null
                && mentak != context.owner()
                && hasFlagshipInOrAdjacentToSystem(context.game(), context.tile(), context.holder(), mentak);
    }

    private static boolean groundForceInSpaceWithAlastorOne(SustainContext context, UnitModel unit) {
        return context.inSpace() && groundForceFightingWith("sigma_nekro_flagship_1", context, unit);
    }

    private static boolean groundForceWithAlastorTwo(SustainContext context, UnitModel unit) {
        return groundForceFightingWith("sigma_nekro_flagship_2", context, unit);
    }

    private static boolean groundForceFightingWith(String flagshipId, SustainContext context, UnitModel unit) {
        return context.hitSource().isSpaceCombatRound()
                && unit.getIsGroundForce()
                && unit.getSustainDamage()
                && ButtonHelper.doesPlayerHaveFSHere(flagshipId, context.owner(), context.tile());
    }

    private static boolean hasFlagshipInOrAdjacentToSystem(
            Game game, Tile tile, UnitHolder unitHolder, Player flagshipOwner) {
        String color = flagshipOwner.getColor();
        if (unitHolder != null && unitHolder.getUnitCount(UnitType.Flagship, color) > 0) {
            return true;
        }
        if (tile == null) {
            return false;
        }
        for (String adjPos : Adjacency.of(game, flagshipOwner).into(tile.getPosition())) {
            Tile adjTile = game.getTileByPosition(adjPos);
            if (adjTile == null) continue;
            UnitHolder adjSpace = adjTile.getSpaceUnitHolder();
            if (adjSpace != null && adjSpace.getUnitCount(UnitType.Flagship, color) > 0) {
                return true;
            }
        }
        return false;
    }
}
