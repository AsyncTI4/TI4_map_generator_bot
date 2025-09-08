package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.RelicHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class MinisterAntiquitiesAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "minister_antiquities";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        MessageHelper.sendMessageToChannel(event.getChannel(), player2.getRepresentation() + " is drawing a relic.");
        RelicHelper.drawRelicAndNotify(player2, event, game);
    }
}
