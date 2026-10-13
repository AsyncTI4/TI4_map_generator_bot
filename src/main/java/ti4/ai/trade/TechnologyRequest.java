package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.strategy.OpeningNeeds;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TechnologyRequest {

    private static final int PAYMENT = 1;
    private static final int LAST_REQUEST_ROUND = 1;
    private static final String SENT_KEY = "technologyRequestSent|";

    static Optional<AiDecision> propose(AiTurnContext context, List<Player> partners) {
        Game game = context.game();
        Player seat = context.seat();
        String sentKey = SENT_KEY + game.getRound();
        if (game.getRound() > LAST_REQUEST_ROUND || context.memory().has(sentKey)) return Optional.empty();
        if (seat.getTg() < PAYMENT || !OpeningNeeds.wantsTechnologySoon(game, seat)) return Optional.empty();
        if (!OpeningNeeds.canFollowTechnologyAfterPaying(game, seat, PAYMENT)) return Optional.empty();
        Optional<Player> holder = OpeningNeeds.technologyHolder(game, seat).filter(partners::contains);
        if (holder.isEmpty()) return Optional.empty();
        Deal deal = Deal.EMPTY.adjust(seat.getFaction(), holder.get().getFaction(), ItemType.TRADE_GOODS, PAYMENT);
        Optional<AiDecision> start = OfferBuilder.start(context, holder.get(), deal, Purpose.TECHNOLOGY_REQUEST);
        start.ifPresent(ignored -> context.memory().put(sentKey, holder.get().getFaction()));
        return start;
    }

    static void noteGift(AiTurnContext context, Player offerer, Deal deal) {
        Game game = context.game();
        Player seat = context.seat();
        if (game.getRound() > LAST_REQUEST_ROUND || !OpeningNeeds.holdsUnplayedTechnology(game, seat)) return;
        String ours = seat.getFaction();
        boolean gift = deal.received(ours, ItemType.TRADE_GOODS) > 0
                && deal.total(ours, ItemType.TRADE_GOODS) == 0
                && deal.total(ours, ItemType.COMMODITIES) == 0
                && deal.notes(ours).isEmpty();
        if (gift) context.memory().put(OpeningNeeds.technologyRequestedKey(game), offerer.getFaction());
    }
}
