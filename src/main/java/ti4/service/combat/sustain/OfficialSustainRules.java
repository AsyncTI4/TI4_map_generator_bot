package ti4.service.combat.sustain;

import java.util.List;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.model.UnitModel;

public final class OfficialSustainRules implements SustainCatalog {

    public static final SustainCatalog CATALOG = new OfficialSustainRules();

    private static final String ARTICLES_OF_WAR = "articles_war";

    private static final List<SustainRule> RULES = List.of(
            SustainRule.suppressor("schematics", OfficialSustainRules::warSunUnderSchematics),
            SustainRule.suppressor("mentak_flagship", OfficialSustainRules::shipNextToMentakFlagship),
            SustainRule.suppressor("mentak_mech", OfficialSustainRules::groundForceNextToMentakMech),
            SustainRule.abilityLoss("crimson_flagship", OfficialSustainRules::underQuietus),
            SustainRule.grant("nomad_mech", OfficialSustainRules::quantumManipulatorCoversShips),
            SustainRule.suppressor(ARTICLES_OF_WAR, OfficialSustainRules::quantumManipulatorUnderArticlesOfWar),
            SustainRule.grant("nekro_flagship", OfficialSustainRules::groundForceFightingWithTheAlastor),
            SustainRule.grant("naaz_voltron", OfficialSustainRules::eidolonMaximumInSpaceCombat),
            SustainRule.grant("metalivoidshielding", OfficialSustainRules::nonFighterShipWithMetaliVoidShielding));

    private static final List<CancelsTwoHitsRule> CANCELS_TWO_HITS = List.of(
            new CancelsTwoHitsRule("nes", (context, unit) -> context.owner().hasTech("nes")));

    @Override
    public List<SustainRule> rules() {
        return RULES;
    }

    @Override
    public List<CancelsTwoHitsRule> cancelsTwoHits() {
        return CANCELS_TWO_HITS;
    }

    private static boolean warSunUnderSchematics(SustainContext context, UnitModel unit) {
        return "warsun".equalsIgnoreCase(unit.getBaseType()) && ButtonHelper.isLawInPlay(context.game(), "schematics");
    }

    private static boolean shipNextToMentakFlagship(SustainContext context, UnitModel unit) {
        Player mentak = Helper.getPlayerFromUnit(context.game(), "mentak_flagship");
        return unit.getIsShip()
                && mentak != null
                && mentak != context.owner()
                && context.tile() != null
                && context.tile().getSpaceUnitHolder().getUnitCount(UnitType.Flagship, mentak.getColor()) > 0;
    }

    private static boolean groundForceNextToMentakMech(SustainContext context, UnitModel unit) {
        Player mentak = Helper.getPlayerFromUnit(context.game(), "mentak_mech");
        return unit.getIsGroundForce()
                && !context.inSpace()
                && mentak != null
                && mentak != context.owner()
                && context.holder().getUnitCount(UnitType.Mech, mentak.getColor()) > 0
                && !ButtonHelper.isLawInPlay(context.game(), ARTICLES_OF_WAR);
    }

    private static boolean underQuietus(SustainContext context, UnitModel unit) {
        return context.holder() != null
                && TeHelperUnits.affectedByQuietus(context.game(), context.owner(), context.holder());
    }

    private static boolean quantumManipulatorCoversShips(SustainContext context, UnitModel unit) {
        return unit.getUnitType() == UnitType.Mech
                && context.inSpace()
                && context.hitSource().isSpaceCombatRound()
                && context.owner().hasUnit("nomad_mech");
    }

    private static boolean quantumManipulatorUnderArticlesOfWar(SustainContext context, UnitModel unit) {
        return quantumManipulatorCoversShips(context, unit)
                && ButtonHelper.isLawInPlay(context.game(), ARTICLES_OF_WAR);
    }

    private static boolean groundForceFightingWithTheAlastor(SustainContext context, UnitModel unit) {
        return context.hitSource().isSpaceCombatRound()
                && unit.getIsGroundForce()
                && unit.getSustainDamage()
                && ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", context.owner(), context.tile());
    }

    private static boolean eidolonMaximumInSpaceCombat(SustainContext context, UnitModel unit) {
        return context.hitSource().isSpaceCombatRound()
                && unit.getUnitType() == UnitType.Mech
                && unit.getSustainDamage()
                && context.owner().hasUnit("naaz_voltron");
    }

    private static boolean nonFighterShipWithMetaliVoidShielding(SustainContext context, UnitModel unit) {
        return unit.isNonFighterShip() && context.owner().hasRelic("metalivoidshielding");
    }
}
