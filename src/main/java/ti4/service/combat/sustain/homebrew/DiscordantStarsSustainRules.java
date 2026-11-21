package ti4.service.combat.sustain.homebrew;

import java.util.List;
import ti4.helpers.ButtonHelper;
import ti4.model.UnitModel;
import ti4.service.combat.sustain.SustainCatalog;
import ti4.service.combat.sustain.SustainContext;
import ti4.service.combat.sustain.SustainRule;

public final class DiscordantStarsSustainRules implements SustainCatalog {

    public static final SustainCatalog CATALOG = new DiscordantStarsSustainRules();

    public static final String ENTROPIC_SCAR = "entropic_scar";

    private static final List<SustainRule> RULES = List.of(
            SustainRule.abilityLoss(ENTROPIC_SCAR, DiscordantStarsSustainRules::inAnEntropicScar),
            SustainRule.immunity(
                    "nivynbt",
                    (context, unit) -> context.owner().hasUnlockedBreakthrough("nivynbt"),
                    DiscordantStarsSustainRules::isEntropicScar),
            SustainRule.grant("mortheuscommander", DiscordantStarsSustainRules::komatShipWhileDefending),
            SustainRule.grant("khrask_flagship", DiscordantStarsSustainRules::nonFighterShipWithSplinteringGale));

    @Override
    public List<SustainRule> rules() {
        return RULES;
    }

    public static boolean isEntropicScar(SustainRule suppressor) {
        return ENTROPIC_SCAR.equals(suppressor.id());
    }

    private static boolean inAnEntropicScar(SustainContext context, UnitModel unit) {
        return context.tile() != null
                && context.tile().getTileModel() != null
                && context.tile().isScar(context.game());
    }

    private static boolean komatShipWhileDefending(SustainContext context, UnitModel unit) {
        return unit.isNonFighterShip()
                && !context.owner().isActivePlayer()
                && context.game().playerHasLeaderUnlockedOrAlliance(context.owner(), "mortheuscommander");
    }

    private static boolean nonFighterShipWithSplinteringGale(SustainContext context, UnitModel unit) {
        return unit.isNonFighterShip()
                && ButtonHelper.doesPlayerHaveFSHere("khrask_flagship", context.owner(), context.tile());
    }
}
