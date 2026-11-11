package ti4.ai.eval;

import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class MechRating {

    private static final double WEAK = 0.0;
    private static final double AVERAGE = 0.25;
    private static final double GOOD = 0.5;
    private static final double STRONG = 0.75;
    private static final double VERY_STRONG = 1.0;

    private static final double STAKE_VERY_HIGH = 1.5;
    private static final double STAKE_HIGH = 1.0;
    private static final double STAKE_MEDIUM = 0.6;
    private static final double STAKE_LOW = 0.25;
    private static final double STAKE_NONE = 0.0;

    private static final double NO_ON_BOARD = 0.0;
    private static final double ON_BOARD = 0.3;

    private static final double HOME_ABILITY = 2.5;
    private static final double HOME_GUARD = 2.2;
    private static final double HOME_PLAIN = 2.0;
    private static final double HOME_LOW = 1.6;

    public record Rating(double quality, double articlesStake, double onBoardAbility, double homeWorth) {}

    private static final Rating NO_MECH = new Rating(WEAK, STAKE_NONE, NO_ON_BOARD, 0);
    private static final Rating PRIOR = new Rating(AVERAGE, STAKE_MEDIUM, NO_ON_BOARD, HOME_PLAIN);
    private static final Rating NAAZ = new Rating(STRONG, STAKE_MEDIUM, NO_ON_BOARD, HOME_GUARD);
    private static final Rating FIRMAMENT = new Rating(AVERAGE, STAKE_HIGH, NO_ON_BOARD, HOME_PLAIN);

    private static final Map<String, Rating> RATINGS = Map.ofEntries(
            Map.entry("arborec_mech", new Rating(STRONG, STAKE_VERY_HIGH, ON_BOARD, HOME_ABILITY)),
            Map.entry("argent_mech", new Rating(GOOD, STAKE_MEDIUM, NO_ON_BOARD, HOME_LOW)),
            Map.entry("cabal_mech", new Rating(AVERAGE, STAKE_LOW, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("empyrean_mech", new Rating(STRONG, STAKE_VERY_HIGH, ON_BOARD, HOME_GUARD)),
            Map.entry("ghost_mech", new Rating(STRONG, STAKE_VERY_HIGH, NO_ON_BOARD, HOME_LOW)),
            Map.entry("hacan_mech", new Rating(AVERAGE, STAKE_LOW, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("jolnar_mech", new Rating(WEAK, STAKE_LOW, NO_ON_BOARD, HOME_GUARD)),
            Map.entry("l1z1x_mech", new Rating(AVERAGE, STAKE_MEDIUM, NO_ON_BOARD, HOME_LOW)),
            Map.entry("letnev_mech", new Rating(VERY_STRONG, STAKE_VERY_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("mahact_mech", new Rating(STRONG, STAKE_VERY_HIGH, NO_ON_BOARD, HOME_GUARD)),
            Map.entry("mentak_mech", new Rating(AVERAGE, STAKE_LOW, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("muaat_mech", new Rating(GOOD, STAKE_MEDIUM, NO_ON_BOARD, HOME_LOW)),
            Map.entry("naalu_mech", new Rating(WEAK, STAKE_LOW, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("naalu_mech_te", new Rating(GOOD, STAKE_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("naalu_mech_omega", new Rating(GOOD, STAKE_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("naaz_mech", NAAZ),
            Map.entry("naaz_mech_space", NAAZ),
            Map.entry("naaz_voltron", NAAZ),
            Map.entry("nekro_mech", new Rating(WEAK, STAKE_LOW, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("nomad_mech", new Rating(GOOD, STAKE_MEDIUM, NO_ON_BOARD, HOME_LOW)),
            Map.entry("saar_mech", new Rating(GOOD, STAKE_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("sardakk_mech", new Rating(VERY_STRONG, STAKE_VERY_HIGH, NO_ON_BOARD, HOME_GUARD)),
            Map.entry("sol_mech", new Rating(GOOD, STAKE_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("titans_mech", new Rating(GOOD, STAKE_VERY_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("winnu_mech", new Rating(STRONG, STAKE_VERY_HIGH, NO_ON_BOARD, HOME_LOW)),
            Map.entry("xxcha_mech", new Rating(STRONG, STAKE_VERY_HIGH, ON_BOARD, HOME_ABILITY)),
            Map.entry("yin_mech", new Rating(GOOD, STAKE_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("yssaril_mech", new Rating(STRONG, STAKE_VERY_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("keleres_mech", new Rating(GOOD, STAKE_HIGH, NO_ON_BOARD, HOME_GUARD)),
            Map.entry("bastion_mech", new Rating(AVERAGE, STAKE_MEDIUM, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("deepwrought_mech", new Rating(VERY_STRONG, STAKE_VERY_HIGH, ON_BOARD, HOME_ABILITY)),
            Map.entry("crimson_mech", new Rating(GOOD, STAKE_HIGH, NO_ON_BOARD, HOME_PLAIN)),
            Map.entry("ralnel_mech", new Rating(AVERAGE, STAKE_MEDIUM, NO_ON_BOARD, HOME_GUARD)),
            Map.entry("firmament_mech", FIRMAMENT),
            Map.entry("obsidian_mech", FIRMAMENT),
            Map.entry("mech", new Rating(WEAK, STAKE_NONE, NO_ON_BOARD, HOME_PLAIN)));

    public static Rating of(Player seat) {
        UnitModel mech = seat.getUnitByType(UnitType.Mech);
        return mech == null ? NO_MECH : ofUnit(mech.getId());
    }

    public static Rating ofUnit(String unitId) {
        return RATINGS.getOrDefault(unitId, PRIOR);
    }

    public static Rating prior() {
        return PRIOR;
    }

    public static Set<String> ratedUnits() {
        return RATINGS.keySet();
    }

    public static double articlesStake(Player seat) {
        return of(seat).articlesStake();
    }

    public static double onBoardAbility(Player seat) {
        return of(seat).onBoardAbility();
    }

    public static double homeWorth(Player seat) {
        return of(seat).homeWorth();
    }

    public static double abilityQuality(Player seat) {
        Rating rating = of(seat);
        return rating.quality() + rating.onBoardAbility();
    }
}
