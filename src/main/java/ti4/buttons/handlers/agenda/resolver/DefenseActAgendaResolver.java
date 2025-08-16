package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DefenseActAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "defense_act";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            for (Player player : game.getRealPlayers()) {
                if (!ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Pds)
                        .isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentation() + ", please destroy 1 of your PDS.",
                            ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "pds"));
                }
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Sent buttons for each player to destroy 1 PDS.");
        }
    }
}
