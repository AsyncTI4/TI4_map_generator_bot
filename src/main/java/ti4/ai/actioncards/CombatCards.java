package ti4.ai.actioncards;

import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatForecast.RoundModifier;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class CombatCards {

    static final String EMERGENCY_REPAIRS = "emergency";
    static final String FIGHTER_PROTOTYPE = "f_prototype";
    static final List<String> MORALE_BOOSTS = List.of("mb1", "mb2", "mb3", "mb4", "morale_boost_ds");
    private static final String REPAIRS_HANDLER = "resolveEmergencyRepairs_";
    private static final String MODIFIER_HANDLER = "applytempcombatmod__action_cards__";
    private static final int MORALE_BOOST_BONUS = 1;
    private static final int FIGHTER_PROTOTYPE_BONUS = 2;
    private static final double MORALE_BOOST_MIN_LOSS = 0.2;
    private static final double MORALE_BOOST_MAX_LOSS = 0.8;

    static List<AiCard> all() {
        return Stream.of(
                        Stream.of(AiActionCards.combatRound(EMERGENCY_REPAIRS, REPAIRS_HANDLER)),
                        Stream.concat(MORALE_BOOSTS.stream(), Stream.of(FIGHTER_PROTOTYPE))
                                .map(alias -> AiActionCards.combatRound(alias, MODIFIER_HANDLER + alias)),
                        DirectHitCards.all().stream(),
                        SkilledRetreatCard.all().stream())
                .flatMap(cards -> cards)
                .toList();
    }

    static double valueAtRoundStart(Game game, Player seat, CombatMoment moment, String alias) {
        String position = moment.position();
        String holder = moment.holder();
        if (EMERGENCY_REPAIRS.equals(alias)) return CombatForecast.repairSwing(game, seat, position, holder);
        if (MORALE_BOOSTS.contains(alias)) {
            return CombatForecast.firstRoundSwing(
                    game, seat, position, holder, RoundModifier.allUnits(MORALE_BOOST_BONUS));
        }
        if (FIGHTER_PROTOTYPE.equals(alias) && moment.round() == 0 && !moment.ground()) {
            return CombatForecast.firstRoundSwing(
                    game, seat, position, holder, RoundModifier.fighters(FIGHTER_PROTOTYPE_BONUS));
        }
        if (SkilledRetreatCard.ALIASES.contains(alias)) return SkilledRetreatCard.value(game, seat, moment);
        return 0;
    }

    static boolean closeEnoughForMoraleBoost(Game game, Player seat, CombatMoment moment) {
        return CombatForecast.lossChance(game, seat, moment.position(), moment.holder()).stream()
                .anyMatch(CombatCards::lossWorthAMoraleBoost);
    }

    static boolean lossWorthAMoraleBoost(double lossChance) {
        return lossChance >= MORALE_BOOST_MIN_LOSS && lossChance <= MORALE_BOOST_MAX_LOSS;
    }

    static boolean addsRoundModifier(String alias) {
        return MORALE_BOOSTS.contains(alias) || FIGHTER_PROTOTYPE.equals(alias);
    }

    static String titleOf(CardPlay play) {
        return SabotageWindow.title(play.alias());
    }
}
