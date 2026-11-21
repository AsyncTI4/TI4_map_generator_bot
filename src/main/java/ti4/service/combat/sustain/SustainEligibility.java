package ti4.service.combat.sustain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.helpers.Units.UnitKey;
import ti4.model.UnitModel;
import ti4.service.combat.SustainCatalogs;

@UtilityClass
public class SustainEligibility {

    public static boolean canSustain(SustainContext context, UnitModel unit) {
        if (context == null || context.owner() == null || unit == null) {
            return false;
        }
        List<SustainRule> immunities = new ArrayList<>();
        List<SustainRule> suppressors = new ArrayList<>();
        boolean granted = false;
        for (SustainRule rule : SustainCatalogs.rules()) {
            switch (rule.evaluate(context, unit)) {
                case IMMUNE -> immunities.add(rule);
                case SUPPRESS -> suppressors.add(rule);
                case GRANT -> granted = true;
                case ABSTAIN -> {}
            }
        }
        boolean suppressed = suppressors.stream()
                .anyMatch(suppressor -> immunities.stream().noneMatch(immunity -> immunity.shieldsFrom(suppressor)));
        if (suppressed) {
            return false;
        }
        return granted || (unit.getSustainDamage() && isHitHereByPrintedRules(context, unit));
    }

    public static int hitsCancelled(SustainContext context, UnitModel unit) {
        if (context == null || context.owner() == null || unit == null) {
            return 1;
        }
        boolean cancelsTwo = SustainCatalogs.cancelsTwoHits().stream().anyMatch(rule -> rule.applies(context, unit));
        return cancelsTwo ? 2 : 1;
    }

    public static Set<UnitKey> eligibleUnits(SustainContext context) {
        Set<UnitKey> eligible = new LinkedHashSet<>();
        if (context == null || context.owner() == null || context.holder() == null) {
            return eligible;
        }
        for (UnitKey unitKey : context.holder().getUnitKeys()) {
            if (!context.owner().unitBelongsToPlayer(unitKey)) continue;
            int undamaged =
                    context.holder().getUnitCount(unitKey) - context.holder().getDamagedUnitCount(unitKey);
            if (undamaged < 1) continue;
            if (canSustain(context, context.owner().getUnitFromUnitKey(unitKey))) {
                eligible.add(unitKey);
            }
        }
        return eligible;
    }

    private static boolean isHitHereByPrintedRules(SustainContext context, UnitModel unit) {
        return switch (context.hitSource()) {
            case SPACE_COMBAT, AFB -> context.inSpace() && unit.getIsShip();
            case SPACE_CANNON -> !context.inSpace() || unit.getIsShip();
            case GROUND_COMBAT, BOMBARDMENT, ABILITY -> true;
        };
    }
}
