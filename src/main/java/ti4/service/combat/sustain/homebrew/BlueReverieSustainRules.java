package ti4.service.combat.sustain.homebrew;

import java.util.List;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.XinTechHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.model.UnitModel;
import ti4.service.combat.sustain.SustainCatalog;
import ti4.service.combat.sustain.SustainContext;
import ti4.service.combat.sustain.SustainRule;

public final class BlueReverieSustainRules implements SustainCatalog {

    public static final SustainCatalog CATALOG = new BlueReverieSustainRules();

    private static final List<SustainRule> RULES = List.of(
            SustainRule.abilityLoss("superweaponglatison", BlueReverieSustainRules::nearAnotherPlayersGlatison),
            SustainRule.abilityLoss("belkosea_monument", BlueReverieSustainRules::nearArmageddonProjectGlatison),
            SustainRule.immunity(
                    "dsxinystar",
                    (context, unit) -> XinTechHandler.hasVeiledNetworkingStar(context.owner()),
                    SustainRule::takesAbilityAway));

    @Override
    public List<SustainRule> rules() {
        return RULES;
    }

    private static boolean nearAnotherPlayersGlatison(SustainContext context, UnitModel unit) {
        return context.tile() != null
                && context.game().getRealPlayersNNeutral().stream()
                        .anyMatch(other -> other != context.owner()
                                && other.hasRelic("superweaponglatison")
                                && context.tile()
                                        == ButtonHelperAbilities.getLocationOfSuperweapon(context.game(), "glatison"));
    }

    private static boolean nearArmageddonProjectGlatison(SustainContext context, UnitModel unit) {
        return context.tile() != null
                && MonumentsBRButtonHandler.armageddonProjectGlatisonCovers(
                        context.game(), context.owner(), context.tile());
    }
}
