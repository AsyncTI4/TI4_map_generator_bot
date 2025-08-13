package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

public class WarrantAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "warrant";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        player2.flipSearchWarrant();
        game.drawSecretObjective(player2.getUserID());
        game.drawSecretObjective(player2.getUserID());
        if (player2.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player2.getUserID());
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player2, event);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (game.isFowMode() ? "The elected player" : player2.getRepresentation())
                        + " has drawn 2 secret objectives, and their secret objective info is now public.");
    }
}
