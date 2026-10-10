package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class FlagshipRating {

    public static final double GOOD = 0.7;
    private static final double UNKNOWN = 0.5;
    private static final Map<String, Double> RATINGS = Map.ofEntries(
            Map.entry("letnev_flagship", 1.0),
            Map.entry("nomad_flagship2", 1.0),
            Map.entry("mahact_flagship", 0.85),
            Map.entry("l1z1x_flagship", 0.85),
            Map.entry("mentak_flagship", 0.85),
            Map.entry("nomad_flagship", 0.8),
            Map.entry("arborec_flagship", 0.8),
            Map.entry("sardakk_flagship", 0.8),
            Map.entry("jolnar_flagship", 0.75),
            Map.entry("cabal_flagship", 0.75),
            Map.entry("xxcha_flagship", 0.7),
            Map.entry("sol_flagship", 0.7),
            Map.entry("yssaril_flagship", 0.65),
            Map.entry("nekro_flagship", 0.6),
            Map.entry("saar_flagship", 0.6),
            Map.entry("empyrean_flagship", 0.6),
            Map.entry("titans_flagship", 0.6),
            Map.entry("naalu_flagship", 0.55),
            Map.entry("winnu_flagship", 0.55),
            Map.entry("ghost_flagship", 0.45),
            Map.entry("argent_flagship", 0.45),
            Map.entry("naaz_flagship", 0.45),
            Map.entry("keleres_flagship", 0.45),
            Map.entry("hacan_flagship", 0.4),
            Map.entry("muaat_flagship", 0.35),
            Map.entry("yin_flagship", 0.3));

    public static double of(Player seat) {
        UnitModel flagship = seat.getUnitByType(UnitType.Flagship);
        return flagship == null ? 0 : RATINGS.getOrDefault(flagship.getId(), UNKNOWN);
    }

    public static boolean isGood(Player seat) {
        return of(seat) >= GOOD;
    }
}
