package ti4.buttons.handlers.agenda.resolver;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class GrantReallocationAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "grant_reallocation";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player2.getCorrectChannel(),
                player2.getRepresentation() + ", please choose a technology to gain.",
                List.of(Buttons.GET_A_TECH));

        List<Button> buttons = ButtonHelper.getLoseFleetCCButtons(player2);
        MessageHelper.sendMessageToChannelWithButtons(
                player2.getCorrectChannel(),
                player2.getRepresentation()
                        + ", after you have gained your technology, please remove one token from your fleet pool for each prerequisite on that technology.",
                buttons);
    }
}
