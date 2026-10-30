package ti4.ai.trade;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.SpendUnlock;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class DebtRules {

    static final int DEBT_CAP = 4;
    static final String PAY_PREFIX = "sendTGTo_";
    private static final String SEPARATOR = "_";
    private static final int BULK = 3;
    private static final Set<ItemType> PAYMENT = EnumSet.of(ItemType.TRADE_GOODS, ItemType.COMMODITIES);

    private enum Payment {
        COMMODITIES_3("comm3", ItemType.COMMODITIES, BULK),
        COMMODITY("comm", ItemType.COMMODITIES, 1),
        TRADE_GOODS_3("tg3", ItemType.TRADE_GOODS, BULK),
        TRADE_GOOD("tg", ItemType.TRADE_GOODS, 1);

        private final String suffix;
        private final ItemType type;
        private final int amount;

        Payment(String suffix, ItemType type, int amount) {
            this.suffix = suffix;
            this.type = type;
            this.amount = amount;
        }
    }

    static int room(Player debtor, Player creditor) {
        return Math.max(0, DEBT_CAP - creditor.getDebtTokenCount(debtor.getColor()));
    }

    static Optional<AiDecision> payInAgenda(AiTurnContext context) {
        if (!TradeLegality.isAgendaPhase(context.game())) return Optional.empty();
        for (AiPrompt prompt : paymentPrompts(context)) {
            Optional<AiDecision> press = payFrom(context, prompt);
            if (press.isPresent()) return press;
        }
        return Optional.empty();
    }

    static boolean paymentPromptVisible(AiTurnContext context, Player creditor) {
        String prefix = PAY_PREFIX + creditor.getFaction() + SEPARATOR;
        return paymentPrompts(context).stream()
                .anyMatch(prompt -> prompt.firstEnabled(button ->
                                button.isUnowned() && button.handlerId().startsWith(prefix))
                        .isPresent());
    }

    static Optional<Deal> payment(Player debtor, Player creditor, int spareTradeGoods, int spareCommodities) {
        int debt = creditor.getDebtTokenCount(debtor.getColor());
        int amount = Math.min(debt, Math.max(0, spareCommodities) + Math.max(0, spareTradeGoods));
        if (amount < 1) return Optional.empty();
        String ours = debtor.getFaction();
        String theirs = creditor.getFaction();
        int inCommodities = Math.min(amount, Math.max(0, spareCommodities));
        return Optional.of(Deal.EMPTY
                .adjust(theirs, ours, ItemType.CLEAR_DEBT, amount)
                .adjust(ours, theirs, ItemType.COMMODITIES, inCommodities)
                .adjust(ours, theirs, ItemType.TRADE_GOODS, amount - inCommodities));
    }

    static Optional<Deal> collection(Player creditor, Player debtor) {
        int debt = creditor.getDebtTokenCount(debtor.getColor());
        int commodities = Math.min(debt, debtor.getCommodities());
        int tradeGoods = Math.min(debt - commodities, debtor.getTg());
        int total = commodities + tradeGoods;
        if (total < 1) return Optional.empty();
        String ours = creditor.getFaction();
        String theirs = debtor.getFaction();
        return Optional.of(Deal.EMPTY
                .adjust(theirs, ours, ItemType.COMMODITIES, commodities)
                .adjust(theirs, ours, ItemType.TRADE_GOODS, tradeGoods)
                .adjust(ours, theirs, ItemType.CLEAR_DEBT, total));
    }

    static boolean defers(Game game, Player creditor, Deal deal) {
        int net = deal.netTradeGoodsTo(creditor.getFaction());
        return Stinginess.nearWin(game, creditor) && net > 0 && SpendUnlock.pointDelta(game, creditor, net) > 0;
    }

    static boolean owedCollection(AiTurnContext context, Player creditor, Deal deal) {
        Player seat = context.seat();
        String ours = seat.getFaction();
        String theirs = creditor.getFaction();
        int debt = creditor.getDebtTokenCount(seat.getColor());
        int cleared = deal.total(theirs, ItemType.CLEAR_DEBT);
        boolean onlyClears = deal.items().stream()
                .filter(item -> item.isFrom(theirs))
                .allMatch(item -> item.type() == ItemType.CLEAR_DEBT);
        boolean onlyPays =
                deal.items().stream().filter(item -> item.isFrom(ours)).allMatch(item -> PAYMENT.contains(item.type()));
        return cleared >= 1 && cleared <= debt && onlyClears && onlyPays && paid(deal, ours) <= cleared;
    }

    static boolean repays(AiTurnContext context, Player debtor, Deal deal) {
        Player seat = context.seat();
        String ours = seat.getFaction();
        String theirs = debtor.getFaction();
        int owed = seat.getDebtTokenCount(debtor.getColor());
        int cleared = deal.total(ours, ItemType.CLEAR_DEBT);
        boolean onlyClears = deal.items().stream()
                .filter(item -> item.isFrom(ours))
                .allMatch(item -> item.type() == ItemType.CLEAR_DEBT);
        boolean onlyPays = deal.items().stream()
                .filter(item -> item.isFrom(theirs))
                .allMatch(item -> PAYMENT.contains(item.type()));
        return cleared >= 1 && cleared <= owed && onlyClears && onlyPays && paid(deal, theirs) >= cleared;
    }

    static Optional<Deal> payablePart(AiTurnContext context, Player creditor, Deal deal) {
        Player seat = context.seat();
        String ours = seat.getFaction();
        String theirs = creditor.getFaction();
        int commodities = TradeBudget.spareCommodities(context, creditor);
        int tradeGoods = TradeBudget.spareTradeGoods(context.game(), seat, creditor);
        int amount = Math.min(paid(deal, ours), commodities + tradeGoods);
        if (amount < 1) return Optional.empty();
        int inCommodities = Math.min(amount, commodities);
        return Optional.of(Deal.EMPTY
                .adjust(theirs, ours, ItemType.CLEAR_DEBT, amount)
                .adjust(ours, theirs, ItemType.COMMODITIES, inCommodities)
                .adjust(ours, theirs, ItemType.TRADE_GOODS, amount - inCommodities));
    }

    private static List<AiPrompt> paymentPrompts(AiTurnContext context) {
        Player seat = context.seat();
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .filter(prompt -> addressedTo(prompt, seat))
                .filter(prompt -> prompt.hasHandlerPrefix(PAY_PREFIX))
                .toList();
    }

    private static boolean addressedTo(AiPrompt prompt, Player seat) {
        String content = StringUtils.defaultString(prompt.content());
        return content.contains(seat.getUserID()) || content.startsWith(seat.getRepresentation());
    }

    private static Optional<AiDecision> payFrom(AiTurnContext context, AiPrompt prompt) {
        Game game = context.game();
        Player seat = context.seat();
        for (PromptButton button : prompt.enabledButtons()) {
            if (!button.isUnowned() || !button.handlerId().startsWith(PAY_PREFIX)) continue;
            String faction = StringUtils.substringBefore(button.handlerId().substring(PAY_PREFIX.length()), SEPARATOR);
            Player creditor = game.getPlayerFromColorOrFaction(faction);
            if (creditor == null || creditor.getDebtTokenCount(seat.getColor()) < 1) continue;
            Optional<AiDecision> press = pay(context, prompt, creditor);
            if (press.isPresent()) return press;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> pay(AiTurnContext context, AiPrompt prompt, Player creditor) {
        Game game = context.game();
        Player seat = context.seat();
        int debt = creditor.getDebtTokenCount(seat.getColor());
        int commodities = TradeBudget.freeCommodities(context);
        int tradeGoods = TradeBudget.freeTradeGoods(game, seat);
        Optional<Deal> whole = payment(seat, creditor, tradeGoods, commodities);
        if (whole.isEmpty() || defers(game, creditor, whole.get())) return Optional.empty();
        for (Payment payment : Payment.values()) {
            int held = payment.type == ItemType.COMMODITIES ? commodities : tradeGoods;
            if (payment.amount > debt || payment.amount > held) continue;
            String handlerId = PAY_PREFIX + creditor.getFaction() + SEPARATOR + payment.suffix;
            Optional<PromptButton> button =
                    prompt.firstEnabled(candidate -> candidate.isUnowned() && handlerId.equals(candidate.handlerId()));
            if (button.isPresent()) {
                return Optional.of(AiDecision.press(
                        prompt, button.get(), "trade: pay debt to " + creditor.getFaction() + " in the agenda phase"));
            }
        }
        return Optional.empty();
    }

    private static int paid(Deal deal, String faction) {
        return deal.total(faction, ItemType.TRADE_GOODS) + deal.total(faction, ItemType.COMMODITIES);
    }
}
