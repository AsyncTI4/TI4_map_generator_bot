package ti4.website.model;

import lombok.Data;
import ti4.map.Game;
import ti4.model.StrategyCardModel;

@Data
public class WebStrategyCard {
    private int initiative;
    private String name;
    private String id;
    private boolean picked;
    private boolean played;
    private boolean exhausted;
    private int tradeGoods;
    private String pickedByFaction;

    public static WebStrategyCard fromGameStrategyCard(int scNumber, Game game) {
        WebStrategyCard webSC = new WebStrategyCard();

        // Basic properties
        webSC.setInitiative(scNumber);
        webSC.setTradeGoods(game.getScTradeGoods().getOrDefault(scNumber, 0));
        webSC.setPlayed(game.getScPlayed().getOrDefault(scNumber, false));
        webSC.setExhausted(!game.getStoredValue("exhaustedSC" + scNumber).isEmpty());

        // Get strategy card model for additional details
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scNumber).orElse(null);
        if (scModel != null) {
            webSC.setName(scModel.getName());
            webSC.setId(scModel.getId());
        }

        // Check if picked and by whom
        for (var player : game.getPlayers().values()) {
            if (player.getSCs().contains(scNumber)) {
                webSC.setPicked(true);
                webSC.setPickedByFaction(player.getFaction());
                break;
            }
        }

        return webSC;
    }
}