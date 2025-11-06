package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class StandardizationAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "standardization";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        player2.setTacticalCC(3);
        player2.setStrategicCC(2);
        int amount = Math.clamp(3 - player2.getMahactCC().size(), 0, 3);
        player2.setFleetCC(amount);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Set " + player2.getFactionEmojiOrColor() + " command sheet to 3/" + player2.getFleetCC() + "/2.");
        ButtonHelper.checkFleetInEveryTile(player2, game);
    }
}
