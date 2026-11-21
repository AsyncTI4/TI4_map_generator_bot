package ti4.service.combat.sustain;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import ti4.model.UnitModel;

record ConditionalSustainRule(
        String id,
        SustainVerdict verdict,
        BiPredicate<SustainContext, UnitModel> when,
        boolean takesAbilityAway,
        Predicate<SustainRule> shields)
        implements SustainRule {

    @Override
    public SustainVerdict evaluate(SustainContext context, UnitModel unit) {
        return when.test(context, unit) ? verdict : SustainVerdict.ABSTAIN;
    }

    @Override
    public boolean shieldsFrom(SustainRule suppressor) {
        return shields.test(suppressor);
    }
}
