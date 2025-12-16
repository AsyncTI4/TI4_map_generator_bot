package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.abilities.MahactTokenService;

public class ClandestineAgendaResolver implements ForAgainstAgendaResolver {
    @Override
    public String getAgendaId() {
        return "cladenstine";
    }

    @Override
    public void handleFor(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player player : game.getRealPlayers()) {
            List<Button> buttons = ButtonHelper.getLoseCCButtons(player);
            String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                    + player.getCCRepresentation() + ". Use buttons to lose command tokens.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
    }

    @Override
    public void handleAgainst(Game game, ButtonInteractionEvent event, int agendaNumericId) {
        for (Player player : game.getRealPlayers()) {
            MahactTokenService.removeFleetCC(game, player, "due to _Clandestine Operations_.");
        }
    }
}
