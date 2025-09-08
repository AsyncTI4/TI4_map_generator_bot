package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PoliticalCensureAgendaResolver implements AgendaResolver {

    private final String agendaId;

    public PoliticalCensureAgendaResolver(String agendaId) {
        this.agendaId = agendaId;
    }

    @Override
    public String getAgendaId() {
        return agendaId;
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        StringBuilder message = new StringBuilder();
        Integer poIndex = game.addCustomPO("Political Censure", 1);
        message.append("Custom objective _Political Censure_ has been added.\n");
        game.scorePublicObjective(player2.getUserID(), poIndex);
        if (!game.isFowMode()) {
            message.append(player2.getRepresentation()).append(" scored _Political Censure_.\n");
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
        Helper.checkEndGame(game, player2);
    }
}
