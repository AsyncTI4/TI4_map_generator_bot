package ti4.service.agenda;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

@UtilityClass
public class LookAgendaService {

    public static void lookAtAgendas(Game game, Player player, int count, boolean lookFromBottom) {
        String sb = player.getRepresentationUnfogged() + " here " + (count == 1 ? "is" : "are") + " the agenda" + (count == 1 ? "" : "s") + " you have looked at:";
        List<MessageEmbed> agendaEmbeds = getAgendaEmbeds(count, lookFromBottom, game);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(player, sb, agendaEmbeds);
    }

    private static List<MessageEmbed> getAgendaEmbeds(int count, boolean fromBottom, Game game) {
        List<MessageEmbed> agendaEmbeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String agendaID = fromBottom ? game.lookAtBottomAgenda(i) : game.lookAtTopAgenda(i);
            if (agendaID != null) {
                AgendaModel agenda = Mapper.getAgenda(agendaID);
                if (game.getSentAgendas().get(agendaID) != null) {
                    agendaEmbeds.add(AgendaModel.agendaIsInSomeonesHandEmbed());
                } else {
                    agendaEmbeds.add(agenda.getRepresentationEmbed());
                }
            }
        }
        return agendaEmbeds;
    }
}
