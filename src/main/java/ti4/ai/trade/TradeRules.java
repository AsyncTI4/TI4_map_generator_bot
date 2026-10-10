package ti4.ai.trade;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;

@UtilityClass
public class TradeRules {

    public static Optional<AiDecision> observe(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        Trust.refresh(context);
        TradeEntry.observe(context);
        PendingOffers.observe(context);
        TradeCardRules.observe(context);
        return Optional.empty();
    }

    public static Optional<AiDecision> rescindStale(AiTurnContext context) {
        if (!AiSettings.isTradingEnabled()) return Optional.empty();
        return PendingOffers.rescindStale(context);
    }

    public static Optional<AiDecision> answerOffers(AiTurnContext context) {
        return OfferResponder.answer(context);
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
}
