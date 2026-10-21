package ti4.ai.trade;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.deal.HumanAiDeal;
import ti4.ai.deal.HumanAiDealMessage;
import ti4.ai.deal.HumanAiDealRunner;
import ti4.ai.deal.HumanAiDealStore;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class HumanDealWatch {

    private static final String SEEN_KEY = "humanAiDealSeen|";

    static boolean pending(AiTurnContext context) {
        return HumanAiDealStore.pendingFor(context.game(), context.faction()).isPresent();
    }

    static void observe(AiTurnContext context) {
        Game game = context.game();
        Optional<HumanAiDeal> latest = HumanAiDealStore.latestFor(game, context.faction());
        if (latest.isEmpty()) return;
        HumanAiDeal deal = latest.get();
        if (deal.status() == HumanAiDeal.Status.PENDING && HumanAiDealRunner.expired(game, deal, context.now())) {
            deal = deal.withStatus(HumanAiDeal.Status.EXPIRED);
            HumanAiDealStore.save(game, deal);
            HumanAiDealMessage.disable(deal);
        }
        boolean turnedDown = deal.status() == HumanAiDeal.Status.DENIED || deal.status() == HumanAiDeal.Status.EXPIRED;
        String seen = SEEN_KEY + deal.id();
        if (!turnedDown || context.memory().has(seen)) return;
        context.memory().put(seen, String.valueOf(context.now()));
        Player human = game.getPlayerFromColorOrFaction(deal.humanFaction());
        if (human != null) OfferRejections.rejected(context, human, context.now());
    }
}
