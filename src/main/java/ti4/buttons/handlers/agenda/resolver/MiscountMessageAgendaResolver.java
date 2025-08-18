package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class MiscountMessageAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return agendaId;
    }

    private final String agendaId;

    public MiscountMessageAgendaResolver() {
        this("miscount");
    }

    public MiscountMessageAgendaResolver(String agendaId) {
        this.agendaId = agendaId;
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "# Repealed the _" + Mapper.getAgendaTitleNoCap(winner)
                        + "_ law.\nIt will will now be revealed for the purposes of revoting.\n-# It is technically still in effect during the revote, if relevant.");
    }
}
