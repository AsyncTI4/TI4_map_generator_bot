package ti4.service.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class TradeStrategyCardService {

    public static void doPrimary(Game game, GenericInteractionCreateEvent event, Player player) {
        boolean reacted = false;
        if (event instanceof ButtonInteractionEvent e) {
            reacted = true;
            String msg = " gained 3" + Emojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(3) + " and replenished commodities (" + player.getCommodities() + " -> " + player.getCommoditiesTotal() + Emojis.comm + ")";
            ButtonHelper.addReaction(e, false, false, msg, "");
        }
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        ButtonHelperAgents.resolveArtunoCheck(player, 3);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperStats.replenishComms(event, game, player, reacted);
    }
}
