package ti4.website.model;

import java.util.List;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
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

    /**
     * Hides who holds which strategy card. Playing a card is announced, so {@code played} stays
     * visible along with the pick it implies - but until then, whether a card has been taken is
     * itself private, and who took it is only visible to someone who can already see that player's
     * stats (FoWHelper#canSeeStatsOfPlayer).
     *
     * <p>Trade goods are cleared alongside, because they'd otherwise give the picks away by
     * elimination: unpicked cards accumulate trade goods, and picking one takes them, so a nonzero
     * count marks a card as definitely still unpicked. Blanking every unplayed card leaves them
     * indistinguishable from each other rather than merely unlabelled.
     */
    public static void redactPickers(List<WebStrategyCard> strategyCards, Game game, Player viewer) {
        for (WebStrategyCard card : strategyCards) {
            Player picker =
                    card.pickedByFaction == null ? null : game.getPlayerFromColorOrFaction(card.pickedByFaction);
            if (picker != null && FoWHelper.canSeeStatsOfPlayer(game, picker, viewer)) {
                continue;
            }
            card.pickedByFaction = null;
            if (!card.played) {
                card.picked = false;
                card.tradeGoods = 0;
            }
        }
    }

    public static WebStrategyCard fromGameStrategyCard(int scNumber, Game game) {
        WebStrategyCard webSC = new WebStrategyCard();

        // Basic properties
        webSC.initiative = scNumber;
        webSC.tradeGoods = game.getScTradeGoods().getOrDefault(scNumber, 0);
        webSC.played = game.getScPlayed().getOrDefault(scNumber, false);
        webSC.exhausted = !game.getStoredValue("exhaustedSC" + scNumber).isEmpty();

        // Get strategy card model for additional details
        StrategyCardModel scModel =
                game.getStrategyCardModelByInitiative(scNumber).orElse(null);
        if (scModel != null) {
            webSC.name = scModel.getName();
            webSC.id = scModel.getId();
        }

        // Check if picked and by whom
        for (var player : game.getPlayers().values()) {
            if (player.getSCs().contains(scNumber)) {
                webSC.picked = true;
                webSC.pickedByFaction = player.getFaction();
                break;
            }
        }

        return webSC;
    }
}
