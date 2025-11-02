package ti4.website.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import ti4.map.Expeditions;
import ti4.map.Game;
import ti4.map.Player;

@Data
public class WebExpeditions {

    @Data
    public static class ExpeditionInfo {
        private final String completedBy; // Player color (null if incomplete)
    }

    private Map<String, ExpeditionInfo> expeditions;

    public static WebExpeditions fromGame(Game game) {
        if (!game.isThundersEdge()) {
            return null;
        }

        // Expeditions should not be returned if the mode is Twilight's Fall
        if (game.isTwilightsFallMode()) {
            return null;
        }

        WebExpeditions web = new WebExpeditions();
        Expeditions exp = game.getExpeditions();

        web.expeditions = new HashMap<>();
        web.expeditions.put("techSkip", createExpeditionInfo(exp.getTechSkip(), game));
        web.expeditions.put("tradeGoods", createExpeditionInfo(exp.getTradeGoods(), game));
        web.expeditions.put("fiveRes", createExpeditionInfo(exp.getFiveRes(), game));
        web.expeditions.put("fiveInf", createExpeditionInfo(exp.getFiveInf(), game));
        web.expeditions.put("secret", createExpeditionInfo(exp.getSecret(), game));
        web.expeditions.put("actionCards", createExpeditionInfo(exp.getActionCards(), game));

        return web;
    }

    private static ExpeditionInfo createExpeditionInfo(String completedByFaction, Game game) {
        String color = null;
        if (completedByFaction != null) {
            Player player = game.getPlayerFromColorOrFaction(completedByFaction);
            if (player != null) {
                color = player.getColor();
            }
        }
        return new ExpeditionInfo(color);
    }
}
