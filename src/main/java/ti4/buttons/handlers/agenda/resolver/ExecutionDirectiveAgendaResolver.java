package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ExecutionDirectiveAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "execution";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        String message =
                "Discarded the elected player's action cards and marked them as unable to vote on the next agenda.";
        ActionCardHelper.discardRandomAC(event, game, player2, player2.getAc());
        game.setStoredValue("PublicExecution", player2.getFaction());
        if (game.getSpeakerUserID().equalsIgnoreCase(player2.getUserID())) {
            boolean foundSpeaker = false;
            for (Player p4 : game.getRealPlayers()) {
                if (foundSpeaker) {
                    game.setSpeakerUserID(p4.getUserID());
                    message += " Also passed the Speaker token to " + p4.getRepresentation() + ".";
                    break;
                }
                if (p4 == player2) {
                    foundSpeaker = true;
                }
            }
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
    }
}
