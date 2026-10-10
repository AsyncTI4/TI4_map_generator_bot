package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class FlagshipRating {

    private static final double S_TIER = 1.0;
    private static final double A_TIER = 0.8;
    private static final double B_TIER = 0.6;
    private static final double C_TIER = 0.4;
    private static final double D_TIER = 0.2;
    public static final double GOOD = A_TIER;
    private static final double UNKNOWN = 0.5;
    private static final Map<String, Double> RATINGS = Map.ofEntries(
            Map.entry("nekro_flagship", S_TIER),
            Map.entry("ghost_flagship", S_TIER),
            Map.entry("nomad_flagship", S_TIER),
            Map.entry("nomad_flagship2", S_TIER),
            Map.entry("l1z1x_flagship", S_TIER),
            Map.entry("deepwrought_flagship", S_TIER),
            Map.entry("crimson_flagship", S_TIER),
            Map.entry("xxcha_flagship", S_TIER),
            Map.entry("yin_flagship", A_TIER),
            Map.entry("sol_flagship", A_TIER),
            Map.entry("naalu_flagship", A_TIER),
            Map.entry("yssaril_flagship", A_TIER),
            Map.entry("empyrean_flagship", A_TIER),
            Map.entry("keleres_flagship", A_TIER),
            Map.entry("naaz_flagship", A_TIER),
            Map.entry("mahact_flagship", A_TIER),
            Map.entry("jolnar_flagship", B_TIER),
            Map.entry("winnu_flagship", B_TIER),
            Map.entry("letnev_flagship", B_TIER),
            Map.entry("mentak_flagship", B_TIER),
            Map.entry("cabal_flagship", B_TIER),
            Map.entry("muaat_flagship", C_TIER),
            Map.entry("saar_flagship", C_TIER),
            Map.entry("titans_flagship", C_TIER),
            Map.entry("argent_flagship", D_TIER),
            Map.entry("arborec_flagship", D_TIER),
            Map.entry("ralnel_flagship", D_TIER),
            Map.entry("sardakk_flagship", D_TIER),
            Map.entry("hacan_flagship", D_TIER));

    public static double of(Player seat) {
        UnitModel flagship = seat.getUnitByType(UnitType.Flagship);
        return flagship == null ? 0 : RATINGS.getOrDefault(flagship.getId(), UNKNOWN);
    }

    public static boolean isGood(Player seat) {
        return of(seat) >= GOOD;
    }
}
