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
        Player p2 = game.getPlayerFromColorOrFaction(winner);
        if (p2 == null) return;
        p2.setTacticalCC(3);
        p2.setStrategicCC(2);
        int amountToGain = Math.clamp(3 - p2.getEffectiveFleetCC(), 0, 3);
        p2.gainFleetCC(amountToGain);

        String msg = "Set " + p2.getFactionEmojiOrColor() + " command sheet to 3/" + p2.getFleetCC() + "/2.";
        MessageHelper.sendMessageToChannel(event.getChannel(), msg);
        if (p2.getEffectiveFleetCC() > 3) {
            msg = p2.getRepresentation()
                    + ", please lose command tokens from your fleet pool until you are at 4 total.";
            var buttons = ButtonHelper.getLoseFleetCCButtons(p2);
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons);
        }
        ButtonHelper.checkFleetInEveryTile(p2, game);
    }
}
