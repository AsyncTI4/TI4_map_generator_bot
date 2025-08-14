package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.CommandCounterHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class SharedResearchAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "shared_research";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            for (Player player : game.getRealPlayers()) {
                Tile tile = player.getHomeSystemTile();
                if (tile != null) {
                    CommandCounterHelper.addCC(event, player, tile);
                }
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "A command token from each player has been placed in their home system.");
        }
    }
}
