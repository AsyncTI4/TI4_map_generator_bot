package ti4.ai.trade;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.actioncards.AiActionCards;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
class TradeActionCards {

    static final double UNKNOWN_CARD = 0.5;
    private static final String ARBITERS = "arbiters";
    private static final String GUILD_SHIPS_TECH = "tf-guildships";

    static boolean tradable(Player first, Player second) {
        return tradesCards(first) || tradesCards(second);
    }

    static boolean blocked(Player seat, Player partner, Deal deal) {
        return deal.has(ItemType.ACTION_CARDS) && !tradable(seat, partner);
    }

    static Optional<String> aliasOf(Player holder, String detail) {
        if (!StringUtils.isNumeric(detail)) return Optional.empty();
        int id = Integer.parseInt(detail);
        return holder.getActionCards().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() == id)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    static boolean held(Player seat, DealItem item) {
        return !item.isGenericCard() && aliasOf(seat, item.detail()).isPresent();
    }

    static DealItem item(Player seat, Player receiver, String alias) {
        return new DealItem(
                seat.getFaction(),
                receiver.getFaction(),
                ItemType.ACTION_CARDS,
                String.valueOf(seat.getActionCards().get(alias)));
    }

    static double receiveValue(Game game, Player seat, Player from, DealItem item, boolean seatPrivate) {
        if (!tradable(seat, from)) return 0;
        if (item.isGenericCard()) return UNKNOWN_CARD * item.amount();
        Optional<String> alias = aliasOf(from, item.detail());
        if (alias.isEmpty()) return UNKNOWN_CARD;
        if (seatPrivate) return ActionCardPrior.keepPrior(alias.get());
        return Math.max(0, ActionCardValue.keepValue(game, seat, alias.get()) - discardToMakeRoom(game, seat));
    }

    static double giveCost(Game game, Player seat, Player to, DealItem item, boolean seatPrivate) {
        if (!tradable(seat, to)) return 0;
        if (item.isGenericCard()) return seatPrivate ? UNKNOWN_CARD * item.amount() : NotesForTrade.INFINITE;
        Optional<String> alias = aliasOf(seat, item.detail());
        if (alias.isEmpty() || AiActionCards.isSabotage(alias.get())) return NotesForTrade.INFINITE;
        if (seatPrivate) return ActionCardPrior.keepPrior(alias.get());
        return ActionCardValue.keepValue(game, seat, alias.get());
    }

    static double worthToTable(Player sender, Player receiver, DealItem item) {
        if (!tradable(sender, receiver)) return 0;
        if (item.isGenericCard()) return UNKNOWN_CARD * item.amount();
        return aliasOf(sender, item.detail()).map(ActionCardPrior::keepPrior).orElse(UNKNOWN_CARD);
    }

    private static double discardToMakeRoom(Game game, Player seat) {
        if (seat.getAcCount() < ButtonHelper.getACLimit(game, seat)) return 0;
        List<String> order = ActionCardValue.discardOrder(game, seat);
        return order.isEmpty() ? 0 : ActionCardValue.keepValue(game, seat, order.getFirst());
    }

    private static boolean tradesCards(Player player) {
        return player.hasAbility(ARBITERS) || player.hasTech(GUILD_SHIPS_TECH);
    }
}
