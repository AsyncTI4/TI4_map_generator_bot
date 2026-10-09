package ti4.ai.trade;

import java.util.ArrayList;
import java.util.List;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;

/**
 * The bot's transaction messages as an AI seat sees them in its cards-info thread, built the way
 * {@code TransactionHelper} builds them (ids only; labels do not matter to the AI).
 */
final class TradeButtons {

    private TradeButtons() {}

    /** The cards-info "You may use these buttons to do various things" message. */
    static AiPrompt entry(String id, long created) {
        return hidden(id, created, List.of("cardsInfo", "transaction", "getModifyTiles"));
    }

    /** What pressing {@code transaction} posts: one owned button per other player. */
    static AiPrompt playerPicker(String id, long created, Player seat, Player... others) {
        List<String> ids = new ArrayList<>();
        for (Player other : others) ids.add("FFCC_" + seat.getFaction() + "_transactWith_" + other.getFaction());
        return hidden(id, created, ids);
    }

    /**
     * The builder {@code seat} sees while building an offer to the other party; {@code p1} is the side whose items
     * the rows add (the seat itself in offer mode, the partner in request mode).
     */
    static AiPrompt builder(String id, long created, Player seat, Player p1, Player p2) {
        String pair = p1.getColor() + "_" + p2.getColor();
        List<String> ids = new ArrayList<>();
        if (p1.getTg() > 0) ids.add("newTransact_TGs_" + pair);
        if (p1.getDebtTokenCount(p2.getColor()) > 0) ids.add("newTransact_ClearDebt_" + pair);
        ids.add("newTransact_SendDebt_" + pair);
        if (p1.getCommodities() > 0) ids.add("newTransact_Comms_" + pair);
        boolean offerMode = p1 == seat;
        if (offerMode && (p1.getCommodities() > 0 || p2.getCommodities() > 0)) {
            ids.add("offerToTransact_washComms_" + seat.getColor() + "_" + p2.getColor() + "_0");
        }
        if (!p1.getPromissoryNotes().isEmpty()) ids.add("newTransact_PNs_" + pair);
        ids.add((offerMode ? "newTransact_Details_" : "newTransact_DetailsInvert_") + pair + "_~MDL");
        if (offerMode) {
            ids.add("startReturnPNInPlayArea_" + p2.getColor());
            ids.add("resetOffer_" + p2.getColor());
            ids.add("getNewTransaction_" + p2.getColor() + "_" + p1.getColor());
            ids.add("sendOffer_" + p2.getColor());
        } else {
            ids.add("resetOffer_" + p1.getColor());
            ids.add("getNewTransaction_" + p2.getColor() + "_" + p1.getColor());
            ids.add("sendOffer_" + p1.getColor());
        }
        ids.add("deleteButtons");
        return hidden(id, created, ids);
    }

    /** The amount or item picker that a {@code newTransact_<type>_<sender>_<receiver>} press posts. */
    static AiPrompt picker(String id, long created, String type, Player sender, Player receiver, String... details) {
        List<String> ids = new ArrayList<>();
        for (String detail : details) {
            ids.add("offerToTransact_" + type + "_" + sender.getColor() + "_" + receiver.getColor() + "_" + detail);
        }
        return hidden(id, created, ids);
    }

    /** Amounts 1..{@code most}, as the trade goods, commodities and debt pickers show them. */
    static String[] upTo(int most) {
        String[] amounts = new String[most];
        for (int amount = 1; amount <= most; amount++) amounts[amount - 1] = String.valueOf(amount);
        return amounts;
    }

    /** An offer {@code offerer} sent to the seat: Accept, Reject and "Reject and CounterOffer". */
    static AiPrompt incoming(String id, long created, Player offerer, int number) {
        String color = offerer.getColor();
        return hidden(
                id,
                created,
                List.of("acceptOffer_" + color + "_" + number, "rejectOffer_" + color, "resetOffer_" + color));
    }

    /** The offerer's own copy of a sent offer, with its Rescind button. */
    static AiPrompt sent(String id, long created, Player receiver) {
        return hidden(id, created, List.of("rescindOffer_" + receiver.getColor()));
    }

    static AiPrompt hidden(String id, long created, List<String> ids) {
        return AiTestGame.prompt(id, PromptSource.AI_THREAD, created, ids, List.of());
    }
}
