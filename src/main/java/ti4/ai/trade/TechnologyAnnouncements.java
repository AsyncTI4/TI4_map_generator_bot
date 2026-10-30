package ti4.ai.trade;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.trade.TechnologyRequest.Ask;
import ti4.game.Player;
import ti4.helpers.StringHelper;

@UtilityClass
class TechnologyAnnouncements {

    static final long MAX_AGE_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final String SENDS = " sends ";
    private static final String ASKS = " asks ";
    private static final String EARLY = " to ask them to play Technology soon.";
    private static final String HOLD = " to ask them to hold Technology until later.";
    private static final String HOLD_FOR_FEE = " to hold Technology until they can follow.";

    static String payment(Player payer, Player holder, Deal deal, Ask ask) {
        return AiPrompt.TABLE_TALK + name(payer) + SENDS + name(holder) + " " + amount(deal, payer)
                + (ask == Ask.EARLY ? EARLY : HOLD);
    }

    static String fee(Player holder, Player payer, Deal deal) {
        return AiPrompt.TABLE_TALK + name(holder) + ASKS + name(payer) + " for " + amount(deal, payer) + HOLD_FOR_FEE;
    }

    static Optional<Ask> paymentAsk(AiTurnContext context, Player payer, Player holder) {
        String opening = AiPrompt.TABLE_TALK + name(payer) + SENDS + name(holder) + " ";
        return newest(context, opening).flatMap(content -> {
            if (content.endsWith(EARLY)) return Optional.of(Ask.EARLY);
            if (content.endsWith(HOLD)) return Optional.of(Ask.HOLD);
            return Optional.empty();
        });
    }

    static boolean feeAsked(AiTurnContext context, Player holder, Player payer) {
        String opening = AiPrompt.TABLE_TALK + name(holder) + ASKS + name(payer) + " for ";
        return newest(context, opening)
                .filter(content -> content.endsWith(HOLD_FOR_FEE))
                .isPresent();
    }

    private static Optional<String> newest(AiTurnContext context, String opening) {
        return context.prompts().stream()
                .filter(AiPrompt::isTableTalk)
                .filter(prompt -> context.now() - prompt.createdAtMillis() <= MAX_AGE_MILLIS)
                .filter(prompt -> prompt.content().startsWith(opening))
                .max(Comparator.comparingLong(AiPrompt::createdAtMillis))
                .map(AiPrompt::content);
    }

    private static String amount(Deal deal, Player payer) {
        int tradeGoods = deal.total(payer.getFaction(), ItemType.TRADE_GOODS);
        return tradeGoods > 0
                ? StringHelper.pluralize(tradeGoods, "trade good")
                : StringHelper.pluralize(deal.total(payer.getFaction(), ItemType.SEND_DEBT), "debt");
    }

    private static String name(Player player) {
        return player.getRepresentationNoPing();
    }
}
