package ti4.ai.trade;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class TradeRules {

    public static Optional<AiDecision> observe(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        try (Thinking.Span ignored = Thinking.enter("trade.observe")) {
            Trust.refresh(context);
            TradeEntry.observe(context);
            OfferRejections.observe(context);
            PendingOffers.observe(context);
            TradeCardRules.observe(context);
            HumanDealWatch.observe(context);
            return Optional.empty();
        }
    }

    public static Optional<AiDecision> rescindStale(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return PendingOffers.rescindStale(context);
    }

    public static Optional<AiDecision> answerOffers(AiTurnContext context) {
        return OfferResponder.answer(context);
    }

    public static Optional<AiDecision> askWhatTheyWant(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return OfferRejections.askWhatTheyWant(context);
    }

    public static Optional<AiDecision> payDebtInAgenda(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return DebtRules.payInAgenda(context);
    }

    public static Optional<AiDecision> continueDraft(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return OfferBuilder.continueDraft(context);
    }

    public static Optional<AiDecision> settleTrade(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return TradeCardRules.settle(context);
    }

    public static Optional<AiDecision> startDeals(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return OwnTurnDeals.start(context);
    }

    public static Optional<Player> carthTarget(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return CarthDeals.bestTarget(context);
    }

    public static void rememberCarthTarget(AiTurnContext context, Player target) {
        CarthDeals.remember(context, target);
    }

    public static Optional<Player> rememberedCarthTarget(AiTurnContext context) {
        return CarthDeals.remembered(context);
    }

    public static Optional<AiDecision> carthProposal(AiTurnContext context, Player target) {
        return CarthDeals.propose(context, target);
    }

    public static boolean humanDealPending(AiTurnContext context) {
        return HumanDealWatch.pending(context);
    }

    public static boolean carthAccepts(Game game, Player hacan, Player target, int gain) {
        return CarthDeals.acceptable(game, target, hacan, gain);
    }

    public static Optional<AiDecision> carthPreWash(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return CarthDeals.preWash(context);
    }

    public static Optional<AiDecision> carthDeal(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return CarthDeals.followUp(context);
    }
}
