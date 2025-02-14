package ti4.service.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperStats;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.button.ReactionService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class TradeStrategyCardService {

    public static void doPrimary(Game game, GenericInteractionCreateEvent event, Player player) {
        boolean reacted = false;
        if (event instanceof ButtonInteractionEvent e) {
            reacted = true;
            String msg = " gained 3" + MiscEmojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(3)
                    + " and replenished commodities (" + player.getCommodities() + " -> " + player.getCommoditiesTotal()
                    + MiscEmojis.comm + ")";
            ReactionService.addReaction(e, game, player, msg);
        }
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        ButtonHelperAgents.resolveArtunoCheck(player, 3);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperStats.replenishComms(event, game, player, reacted);
    }
}
