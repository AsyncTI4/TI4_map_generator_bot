package ti4.ai.deal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TransactionHelper;
import ti4.service.leader.agent.AgentLifecycle;

@UtilityClass
public class HumanAiDealRunner {

    private static final String ACTION = "action";

    public static boolean expired(Game game, HumanAiDeal deal, long now) {
        return now >= deal.expiresAt()
                || game.getRound() != deal.round()
                || !ACTION.equalsIgnoreCase(game.getPhaseOfGame());
    }

    public static Optional<String> validate(Game game, HumanAiDeal deal, long now) {
        Player ai = game.getPlayerFromColorOrFaction(deal.aiFaction());
        Player human = game.getPlayerFromColorOrFaction(deal.humanFaction());
        if (ai == null || human == null) return Optional.of("a player in this offer is gone");
        if (expired(game, deal, now)) return Optional.of("the offer has run out");
        Map<String, int[]> balances = new HashMap<>();
        for (Player player : List.of(ai, human)) {
            balances.put(player.getFaction(), new int[] {player.getCommodities(), player.getTg()});
        }
        for (HumanAiDeal.Step step : deal.steps()) {
            Optional<String> failure =
                    switch (step) {
                        case HumanAiDeal.Step.ExhaustLeader exhaust -> checkExhaust(game, ai, exhaust, balances);
                        case HumanAiDeal.Step.Transfer transfer -> checkTransfer(game, transfer, balances);
                    };
            if (failure.isPresent()) return failure;
        }
        return Optional.empty();
    }

    public static void run(ButtonInteractionEvent event, Game game, HumanAiDeal deal) {
        Player ai = game.getPlayerFromColorOrFaction(deal.aiFaction());
        Player human = game.getPlayerFromColorOrFaction(deal.humanFaction());
        List<HumanAiDeal.Step.Transfer> batch = new ArrayList<>();
        for (HumanAiDeal.Step step : deal.steps()) {
            if (step instanceof HumanAiDeal.Step.Transfer transfer) {
                batch.add(transfer);
                continue;
            }
            transferAll(event, game, ai, human, batch);
            HumanAiDeal.Step.ExhaustLeader exhaust = (HumanAiDeal.Step.ExhaustLeader) step;
            AgentLifecycle.press("exhaustAgent_" + exhaust.leaderId() + "_" + exhaust.targetFaction(), event, game, ai);
        }
        transferAll(event, game, ai, human, batch);
    }

    static String item(HumanAiDeal.Step.Transfer transfer) {
        String detail =
                transfer.item() == HumanAiDeal.Item.PROMISSORY ? transfer.detail() : String.valueOf(transfer.amount());
        return "sending" + transfer.fromFaction() + "_receiving" + transfer.toFaction() + "_"
                + transfer.item().token() + "_" + detail;
    }

    private static void transferAll(
            ButtonInteractionEvent event, Game game, Player ai, Player human, List<HumanAiDeal.Step.Transfer> batch) {
        if (batch.isEmpty()) return;
        ai.clearTransactionItemsWithPlayer(human);
        for (HumanAiDeal.Step.Transfer transfer : batch) {
            ai.addTransactionItem(item(transfer));
        }
        batch.clear();
        TransactionHelper.acceptTransactionOffer(ai, human, game, event);
    }

    private static Optional<String> checkExhaust(
            Game game, Player ai, HumanAiDeal.Step.ExhaustLeader exhaust, Map<String, int[]> balances) {
        if (!ai.hasUnexhaustedLeader(exhaust.leaderId())) return Optional.of("the agent is no longer ready");
        Player target = game.getPlayerFromColorOrFaction(exhaust.targetFaction());
        int[] balance = balances.get(exhaust.targetFaction());
        if (target == null || balance == null) return Optional.of("the target is gone");
        if (target.getCommoditiesTotal() - balance[0] < exhaust.minNewCommodities()) {
            return Optional.of("the commodities are no longer low enough for the replenish to matter");
        }
        balance[0] = target.getCommoditiesTotal();
        return Optional.empty();
    }

    private static Optional<String> checkTransfer(
            Game game, HumanAiDeal.Step.Transfer transfer, Map<String, int[]> balances) {
        int[] from = balances.get(transfer.fromFaction());
        int[] to = balances.get(transfer.toFaction());
        if (from == null || to == null) return Optional.of("a player in this offer is gone");
        switch (transfer.item()) {
            case COMMODITIES -> {
                if (from[0] < transfer.amount()) return Optional.of(shortOf(game, transfer, "commodities"));
                from[0] -= transfer.amount();
                to[1] += transfer.amount();
            }
            case TRADE_GOODS -> {
                if (from[1] < transfer.amount()) return Optional.of(shortOf(game, transfer, "trade goods"));
                from[1] -= transfer.amount();
                to[1] += transfer.amount();
            }
            case PROMISSORY -> {
                Player sender = game.getPlayerFromColorOrFaction(transfer.fromFaction());
                if (sender == null || !sender.getPromissoryNotes().containsKey(transfer.detail())) {
                    return Optional.of(shortOf(game, transfer, "promissory note"));
                }
            }
            case DEBT -> {}
        }
        return Optional.empty();
    }

    private static String shortOf(Game game, HumanAiDeal.Step.Transfer transfer, String what) {
        Player sender = game.getPlayerFromColorOrFaction(transfer.fromFaction());
        String name = sender == null ? transfer.fromFaction() : sender.getFactionEmojiOrColor();
        return name + " no longer has enough " + what;
    }
}
