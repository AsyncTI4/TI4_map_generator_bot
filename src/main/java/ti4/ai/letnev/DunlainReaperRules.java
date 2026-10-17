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
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatForecast.FirstRoundGain;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class DunlainReaperRules {

    public static final String MECH = "letnev_mech";
    static final String PREFIX = "letnevMechRes_";
    static final String SUFFIX = "_mech";
    static final int RESOURCES = 2;
    static final double RESOURCE_VALUE = CardValueScale.TRADE_GOOD;
    static final double MIN_GAIN = RESOURCES * RESOURCE_VALUE;
    private static final String KEY = "dunlainReaper|";
    private static final String DONE_PAYING = "deleteButtons";
    private static final String PURPOSE = "Dunlain Reaper";

    public static Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment) {
        Player seat = context.seat();
        if (!moment.ground() || !seat.hasUnit(MECH)) return Optional.empty();
        String key = KEY + moment.position() + "|" + moment.holder() + "|" + moment.round();
        if (context.memory().has(key)) return Optional.empty();
        Optional<Match> button = deployButton(context, moment.holder());
        if (button.isEmpty()) return Optional.empty();
        context.memory().put(key, "decided");
        Optional<Wallet.Payment> payment = payment(context.game(), seat);
        if (payment.isEmpty() || !worthSwapping(context.game(), seat, moment)) return Optional.empty();
        PaymentRules.expect(context, PURPOSE, payment.get(), DONE_PAYING);
        return Optional.of(button.get().press("deploy Dunlain Reaper in place of an infantry"));
    }

    static boolean worthSwapping(Game game, Player seat, CombatMoment moment) {
        FirstRoundGain gain = CombatForecast.mechSwapGain(game, seat, moment.position(), moment.holder());
        return gain.gain() >= MIN_GAIN;
    }

    private static Optional<Wallet.Payment> payment(Game game, Player seat) {
        return ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(RESOURCES));
    }

    private static Optional<Match> deployButton(AiTurnContext context, String planet) {
        List<AiPrompt> combat = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .toList();
        String handler = PREFIX + planet + SUFFIX;
        return Prompts.owned(combat, context.faction(), handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
    }
}
