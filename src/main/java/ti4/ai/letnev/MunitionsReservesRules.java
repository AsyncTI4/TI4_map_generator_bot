package ti4.ai.letnev;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatForecast.FirstRoundGain;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MunitionsReservesRules {

    public static final String BUTTON = "munitionsReserves";
    static final double TRADE_GOOD_VALUE = CardValueScale.TRADE_GOOD;
    static final double MARGIN = 1.25;
    static final double HOPELESS = 0.1;
    private static final String ARMED_KEY = "munitionsReserves";
    private static final String KEY = "munitions|";
    private static final String PRESSED = "pressed";
    private static final String DECLINED = "declined";

    public static Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment) {
        Player seat = context.seat();
        if (moment.ground() || !seat.hasAbility(LetnevSpaceCombat.MUNITIONS_RESERVES)) return Optional.empty();
        if (seat.getTg() < LetnevSpaceCombat.MUNITIONS_COST || armed(context.game(), seat)) return Optional.empty();
        String key = KEY + moment.position() + "|" + moment.round();
        if (context.memory().has(key)) return Optional.empty();
        Optional<Match> button = combatButton(context);
        if (button.isEmpty()) return Optional.empty();
        if (!worthSpending(context.game(), seat, moment.position())) {
            context.memory().put(key, DECLINED);
            return Optional.empty();
        }
        context.memory().put(key, PRESSED);
        return Optional.of(button.get().press("spend 2 trade goods on Munitions Reserves to reroll its misses"));
    }

    static boolean worthSpending(Game game, Player seat, String position) {
        FirstRoundGain gain = CombatForecast.rerollGain(game, seat, position);
        if (gain.winChance() < HOPELESS) return false;
        return gain.gain() >= requiredGain(game, seat);
    }

    static double requiredGain(Game game, Player seat) {
        double cost = LetnevSpaceCombat.MUNITIONS_COST * TRADE_GOOD_VALUE * MARGIN;
        boolean breaksAScoringReserve = ScoringReserve.planAfterReserve(
                        game, seat, SpendCost.tradeGoods(LetnevSpaceCombat.MUNITIONS_COST))
                .isEmpty();
        return breaksAScoringReserve ? cost + ObjectiveValue.VICTORY_POINT_VALUE : cost;
    }

    static boolean armed(Game game, Player seat) {
        return seat.getFaction().equalsIgnoreCase(game.getStoredValue(ARMED_KEY));
    }

    private static Optional<Match> combatButton(AiTurnContext context) {
        List<AiPrompt> combat = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .toList();
        return Prompts.owned(combat, context.faction(), BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
    }
}
