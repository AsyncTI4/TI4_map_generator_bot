package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

public class ElectSecretAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "secret";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        String message;
        if (game.isFowMode()) {
            message = "The elected player drew a secret objective.";
        } else {
            message = player2.getRepresentation() + " drew a secret objective as the elected player.";
        }
        game.drawSecretObjective(player2.getUserID());
        if (player2.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player2.getUserID());
            message += " They drew a second secret objective due to **Plausible Deniability**.";
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player2, event);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
    }
}
