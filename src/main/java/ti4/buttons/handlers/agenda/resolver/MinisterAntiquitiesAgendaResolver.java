package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.RelicHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class MinisterAntiquitiesAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "minister_antiquities";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        RelicHelper.drawRelicAndNotify(player2, event, game);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Drew relic for " + player2.getFactionEmojiOrColor());
    }
}
