package ti4.ai.naalu;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveValue;

@UtilityClass
public class GiftOfPrescience {

    public static final String ALIAS = "gift";
    static final double GIVE_COST = ObjectiveValue.VICTORY_POINT_VALUE;

    public static double giveCost() {
        return GIVE_COST;
    }
}
