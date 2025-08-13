package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class MiscountMessageAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return agId;
    }

    private final String agId;

    public MiscountMessageAgendaResolver() {
        this("miscount");
    }

    public MiscountMessageAgendaResolver(String agId) {
        this.agId = agId;
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "# Repealed the " + Mapper.getAgendaTitleNoCap(winner)
                        + " law and will now reveal it for the purposes of revoting. It is technically still in effect");
    }
}
