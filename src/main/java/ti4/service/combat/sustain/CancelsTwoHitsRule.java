package ti4.service.combat.sustain;

import java.util.function.BiPredicate;
import ti4.model.UnitModel;

public record CancelsTwoHitsRule(String id, BiPredicate<SustainContext, UnitModel> when) {

    public boolean applies(SustainContext context, UnitModel unit) {
        return when.test(context, unit);
    }
}
