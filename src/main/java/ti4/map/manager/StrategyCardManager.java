package ti4.map.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class StrategyCardManager {
    private final Game game;
    private final Map<Integer, Integer> strategyCardToTradeGoodCount = new LinkedHashMap<>();

    public StrategyCardManager(Game game) {
        this.game = game;
    }

    public Map<Integer, Integer> getTradeGoodCounts() {
        return strategyCardToTradeGoodCount;
    }

    public void setTradeGoodCounts(Map<Integer, Integer> strategyCardToTradeGoodCount) {
        this.strategyCardToTradeGoodCount.clear();
        if (strategyCardToTradeGoodCount != null) {
            this.strategyCardToTradeGoodCount.putAll(strategyCardToTradeGoodCount);
        }
    }

    public void setTradeGoodCount(int strategyCard, int tradeGoodCount) {
        if (tradeGoodCount > 0 && strategyCard == ButtonHelper.getKyroHeroSC(game)) {
            Player player = game.getPlayerFromColorOrFaction(game.getStoredValue("kyroHeroPlayer"));
            if (player != null) {
                player.setTg(player.getTg() + tradeGoodCount);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, tradeGoodCount);
                tradeGoodCount = 0;
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(),
                        "The trade goods that would be placed on **" + Helper.getSCName(strategyCard, game)
                                + "** have instead been given to the Kyro "
                                + (game.isFrankenGame() ? "hero " : "")
                                + "player, as per the text on Speygh, the Kyro Hero.");
            }
        }
        strategyCardToTradeGoodCount.put(strategyCard, tradeGoodCount);
    }

    public int addTradeGoods(int strategyCard, int tradeGoodCount) {
        int oldCount = strategyCardToTradeGoodCount.get(strategyCard);
        int newCount = oldCount + tradeGoodCount;
        strategyCardToTradeGoodCount.put(strategyCard, newCount);
        return newCount;
    }

    public void incrementTradeGoods() {
        Set<Integer> pickedStrategyCards = new HashSet<>();
        for (Player player_ : game.getRealPlayers()) {
            pickedStrategyCards.addAll(player_.getSCs());
            if (!player_.getSCs().isEmpty()) {
                StringBuilder scs = new StringBuilder();
                for (int sc : player_.getSCs()) {
                    scs.append(sc).append("_");
                }
                scs = new StringBuilder(scs.substring(0, scs.length() - 1));
                game.setStoredValue("Round" + game.getRound() + "SCPickFor" + player_.getFaction(), scs.toString());
            }
        }

        if (!game.islandMode()) {
            for (Map.Entry<Integer, Integer> entry : strategyCardToTradeGoodCount.entrySet()) {
                Integer scNumber = entry.getKey();
                if (!pickedStrategyCards.contains(scNumber) && scNumber != 0) {
                    Integer tgCount = entry.getValue();
                    tgCount = tgCount == null ? 1 : tgCount + 1;
                    setTradeGoodCount(scNumber, tgCount);
                }
            }
        }
    }

    public boolean add(int strategyCard) {
        if (!strategyCardToTradeGoodCount.containsKey(strategyCard)) {
            strategyCardToTradeGoodCount.put(strategyCard, 0);
            return true;
        }
        return false;
    }

    public boolean remove(int strategyCard) {
        if (strategyCardToTradeGoodCount.containsKey(strategyCard)) {
            strategyCardToTradeGoodCount.remove(strategyCard);
            return true;
        }
        return false;
    }

    public List<Integer> list() {
        return new ArrayList<>(strategyCardToTradeGoodCount.keySet());
    }
}
