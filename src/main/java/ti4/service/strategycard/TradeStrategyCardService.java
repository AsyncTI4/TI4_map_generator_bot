package ti4.service.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperStats;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class TradeStrategyCardService {

    public static void doPrimary(Game game, GenericInteractionCreateEvent event, Player player) {
        boolean reacted = false;
        int oldComm = player.getCommodities();
        if (event instanceof ButtonInteractionEvent e) {
            reacted = true;
        }
        int num = 3;
        if (player.hasTech("tf-futurepath")) {
            num = 9;
        }
        ButtonHelperStats.replenishComms(event, game, player, reacted);
        String gained = " gained " + num + MiscEmojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(num);
        if (event instanceof ButtonInteractionEvent e) {
            String msg = gained + " and replenished commodities (" + oldComm + " -> " + player.getCommodities()
                    + MiscEmojis.comm + ")";
            ReactionService.addReaction(e, game, player, msg);
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentationNoPing() + gained + ".");
        }
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        ButtonHelperAgents.resolveArtunoCheck(player, num);
        ButtonHelperAbilities.pillageCheck(player, game);
    }
}
