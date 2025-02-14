package ti4.service.leader;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.AgendaHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

@UtilityClass
public class NaaluCommanderService {

    public static void secondHalfOfNaaluCommander(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "naalucommander")) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Only players with access to M'aban, the Naalu Commander, unlocked may use this ability.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged()).append(" you are using the M'aban, the Naalu Commander:");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb.toString());

        // Top Agenda
        AgendaHelper.sendTopAgendaToCardsInfoSkipCovert(game, player);

        // Bottom Agenda
        MessageEmbed embed = null;
        sb.setLength(0);
        sb.append("__**Bottom Agenda:**__\n");
        String agendaID = game.lookAtBottomAgenda(0);
        if (game.getSentAgendas().get(agendaID) != null) {
            embed = AgendaModel.agendaIsInSomeonesHandEmbed();
        } else if (agendaID != null) {
            embed = Mapper.getAgenda(agendaID).getRepresentationEmbed();
        } else {
            sb.append("Could not find agenda");
        }
        if (embed != null) {
            MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), sb.toString(), embed);
        } else {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
        }

        sb.setLength(0);
        boolean first = true;
        for (Player player_ : player.getNeighbouringPlayers()) {
            if (!first) sb.append("\n\n");
            first = false;
            sb.append("## ").append(player_.getRepresentation(false, false)).append("'s ");
            sb.append(PromissoryNoteHelper.getPromissoryNoteCardInfo(game, player_, false, true));
        }

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    player.getRepresentation()
                            + " is using M'aban, the Naalu Commander, to look at the top & bottom agenda, and their neighbour's promissory notes.");
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb.toString());
    }
}
