package ti4.ai.eval;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ValueScale {

    public static final double TRADE_GOOD = 1.0;
    public static final double RESOURCE = 0.8;
    public static final double INFLUENCE = 0.8;
    public static final double COMMODITY = 0.8;

    public static double spendable(double resources, double influence) {
        return RESOURCE * resources + INFLUENCE * influence;
    }
}
