package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class NexusAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "nexus";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        if (!"for".equalsIgnoreCase(winner)) {
            Tile tile = game.getMecatolTile();
            if (tile != null) {
                String tokenFilename = Mapper.getTokenID("gamma");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), "Added a gamma wormhole to the Mecatol Rex system.");
            }
        }
    }
}
