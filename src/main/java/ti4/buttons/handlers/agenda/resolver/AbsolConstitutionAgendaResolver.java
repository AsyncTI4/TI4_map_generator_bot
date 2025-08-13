package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

public class AbsolConstitutionAgendaResolver implements AgendaResolver {
    @Override
    public String getAgID() {
        return "absol_constitution";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            java.util.List<String> laws =
                    new java.util.ArrayList<>(game.getLaws().keySet());
            for (String law : laws) {
                game.removeLaw(law);
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# Removed all laws");
            int counter = 40;
            boolean lawFound = false;
            java.util.ArrayList<String> discardedAgendas = new java.util.ArrayList<>();
            while (counter > 0 && !lawFound) {
                counter--;
                String id2 = game.revealAgenda(false);
                AgendaModel agendaDetails = Mapper.getAgenda(id2);
                if (agendaDetails.getType().equalsIgnoreCase("law")) {
                    lawFound = true;
                    game.putAgendaBackIntoDeckOnTop(id2);
                    AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Shuffled the found agendas back in");
                    for (String id3 : discardedAgendas) {
                        game.putAgendaBackIntoDeckOnTop(id3);
                    }
                    game.shuffleAgendas();
                } else {
                    discardedAgendas.add(id2);
                    MessageHelper.sendMessageToChannel(
                            game.getMainGameChannel(), "Found the non-law agenda: " + agendaDetails.getName());
                }
            }
        }
    }
}
