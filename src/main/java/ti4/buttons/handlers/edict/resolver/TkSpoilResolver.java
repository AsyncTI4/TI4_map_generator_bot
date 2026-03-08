package ti4.buttons.handlers.edict.resolver;

import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TkSpoilResolver implements EdictResolver {

    @Getter
    public String edict = "tk-spoil";

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        String addl = "Everyone's trade goods have been modified accordingly:";
        String gain = player.gainTG(3);
        if (game.isFowMode()) {
            addl = "You have gained 3 trade goods " + gain;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), playerPing(player, addl));
        } else {
            addl += "\n> " + player.getFactionEmoji() + " " + gain;
        }

        for (Player p2 : game.getRealPlayers()) {
            if (p2.equals(player)) continue;
            gain = p2.gainTG(-1);
            if (game.isFowMode()) {
                addl = "You have lost 1 trade good " + gain;
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), playerPing(p2, addl));
            } else {
                addl += "\n> " + p2.getFactionEmoji() + " " + gain;
            }
        }

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), gamePing(game, addl));
        }

        ButtonHelperAbilities.pillageCheck(player);
        ButtonHelperAgents.resolveArtunoCheck(player, 3);
    }
}
