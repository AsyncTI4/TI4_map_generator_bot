package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.model.TechnologyModel;

@UtilityClass
public class NetrunnersBreakthroughHandler {

    public static int getDataBreachDiscount(Player netrunner, TechnologyModel tech) {
        int bestDiscount = 0;
        for (Player otherPlayer : netrunner.getGame().getRealPlayersExcludingThis(netrunner)) {
            if (!otherPlayer.hasTech(tech.getAlias())) {
                continue;
            }

            int tokenCount =
                    netrunner.getDebtTokenCount(otherPlayer.getColor(), NetrunnersAbilitiesHandler.SYSTEM_BREACH_POOL);
            bestDiscount = Math.max(bestDiscount, tokenCount / 2);
        }
        return bestDiscount;
    }
}
