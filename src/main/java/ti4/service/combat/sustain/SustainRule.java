package ti4.service.combat.sustain;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import ti4.model.UnitModel;

public interface SustainRule {

    String id();

    SustainVerdict evaluate(SustainContext context, UnitModel unit);

    default boolean takesAbilityAway() {
        return false;
    }

    default boolean shieldsFrom(SustainRule suppressor) {
        return false;
    }

    static SustainRule suppressor(String id, BiPredicate<SustainContext, UnitModel> when) {
        return new ConditionalSustainRule(id, SustainVerdict.SUPPRESS, when, false, suppressor -> false);
    }

    static SustainRule abilityLoss(String id, BiPredicate<SustainContext, UnitModel> when) {
        return new ConditionalSustainRule(id, SustainVerdict.SUPPRESS, when, true, suppressor -> false);
    }

    static SustainRule grant(String id, BiPredicate<SustainContext, UnitModel> when) {
        return new ConditionalSustainRule(id, SustainVerdict.GRANT, when, false, suppressor -> false);
    }

    static SustainRule immunity(
            String id, BiPredicate<SustainContext, UnitModel> when, Predicate<SustainRule> shieldsFrom) {
        return new ConditionalSustainRule(id, SustainVerdict.IMMUNE, when, false, shieldsFrom);
    }
}
