package ti4.service.combat.sustain.twilightsfall;

import java.util.List;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.UnitModel;
import ti4.service.combat.sustain.SustainCatalog;
import ti4.service.combat.sustain.SustainContext;
import ti4.service.combat.sustain.SustainRule;
import ti4.service.combat.sustain.homebrew.DiscordantStarsSustainRules;

public final class TwilightsFallSustainRules implements SustainCatalog {

    public static final SustainCatalog CATALOG = new TwilightsFallSustainRules();

    private static final List<SustainRule> RULES = List.of(
            SustainRule.abilityLoss("tf-smotheringpresence", TwilightsFallSustainRules::inOrNextToSmotheringStructures),
            SustainRule.grant("purpletf_mech", TwilightsFallSustainRules::starlancerInSpaceCombat),
            SustainRule.immunity(
                    "tf-singularitypoint",
                    (context, unit) -> context.owner().hasTech("tf-singularitypoint"),
                    DiscordantStarsSustainRules::isEntropicScar));

    @Override
    public List<SustainRule> rules() {
        return RULES;
    }

    private static boolean inOrNextToSmotheringStructures(SustainContext context, UnitModel unit) {
        if (context.tile() == null) {
            return false;
        }
        for (Player other : context.game().getRealPlayersExcludingThis(context.owner())) {
            if (other.hasTech("tf-smotheringpresence") && hasStructureInOrAdjacentTo(context, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStructureInOrAdjacentTo(SustainContext context, Player structureOwner) {
        for (String position : Adjacency.of(context.game(), structureOwner)
                .withSelf()
                .into(context.tile().getPosition())) {
            Tile tile = context.game().getTileByPosition(position);
            if (tile == null) continue;
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (holder.getUnitCount(UnitType.Pds, structureOwner.getColor()) > 0
                        || holder.getUnitCount(UnitType.Spacedock, structureOwner.getColor()) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean starlancerInSpaceCombat(SustainContext context, UnitModel unit) {
        return context.hitSource().isSpaceCombatRound()
                && unit.getUnitType() == UnitType.Mech
                && unit.getSustainDamage()
                && context.owner().hasUnit("purpletf_mech");
    }
}
