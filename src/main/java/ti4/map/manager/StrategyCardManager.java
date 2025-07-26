package ti4.map.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public Map<Integer, Integer> getTradeGoods() {
        return strategyCardToTradeGoodCount;
    }

    public void setTradeGoods(Map<Integer, Integer> goods) {
        strategyCardToTradeGoodCount.clear();
        if (goods != null) {
            strategyCardToTradeGoodCount.putAll(goods);
        }
    }

    public void setTradeGood(Integer sc, Integer tradeGoodCount) {
        if (Objects.isNull(tradeGoodCount)) {
            tradeGoodCount = 0;
        }
        if (tradeGoodCount > 0 && sc == ButtonHelper.getKyroHeroSC(game)) {
            Player player = game.getPlayerFromColorOrFaction(game.getStoredValue("kyroHeroPlayer"));
            if (player != null) {
                player.setTg(player.getTg() + tradeGoodCount);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, tradeGoodCount);
                tradeGoodCount = 0;
                MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                    "The " + tradeGoodCount + " trade good" + (tradeGoodCount == 1 ? "" : "s")
                        + " that would be placed on **" + Helper.getSCName(sc, game)
                        + "** have instead been given to the Kyro "
                        + (game.isFrankenGame() ? "hero " : "") + "player, as per the text on Speygh, the Kyro Hero.");
            }
        }
        strategyCardToTradeGoodCount.put(sc, tradeGoodCount);
    }

    public void incrementTradeGoods() {
        Set<Integer> scPickedList = new HashSet<>();
        for (Player player_ : game.getRealPlayers()) {
            scPickedList.addAll(player_.getSCs());
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
            for (Integer scNumber : strategyCardToTradeGoodCount.keySet()) {
                if (!scPickedList.contains(scNumber) && scNumber != 0) {
                    Integer tgCount = strategyCardToTradeGoodCount.get(scNumber);
                    tgCount = tgCount == null ? 1 : tgCount + 1;
                    setTradeGood(scNumber, tgCount);
                }
            }
        }
    }

    public boolean addSC(Integer sc) {
        if (!strategyCardToTradeGoodCount.containsKey(sc)) {
            setTradeGood(sc, 0);
            return true;
        }
        return false;
    }

    public boolean removeSC(Integer sc) {
        if (strategyCardToTradeGoodCount.containsKey(sc)) {
            strategyCardToTradeGoodCount.remove(sc);
            return true;
        }
        return false;
    }

    public List<Integer> getSCList() {
        return new ArrayList<>(strategyCardToTradeGoodCount.keySet());
    }
}
