package ti4.commands.special;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.cardspn.PNInfo;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

public class NaaluCommander extends SpecialSubcommandData {

    public NaaluCommander() {
        super(Constants.NAALU_COMMANDER, "Look at your neighbours' promissory notes and the top and bottom of the agenda deck.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        secondHalfOfNaaluCommander(event, game, player);
    }

    @ButtonHandler("naaluCommander")
    public static void secondHalfOfNaaluCommander(GenericInteractionCreateEvent event, Game game, Player player) {

        if (!game.playerHasLeaderUnlockedOrAlliance(player, "naalucommander")) {
            MessageHelper.sendMessageToEventChannel(event, "Only players with access to M'aban, the Naalu Commander, unlocked may use this ability.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged()).append(" you are using the M'aban, the Naalu Commander:");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb.toString());

        // Top Agenda
        sendTopAgendaToCardsInfoSkipCovert(game, player);

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
            sb.append(PNInfo.getPromissoryNoteCardInfo(game, player_, false, true));
        }

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), player.getRepresentation() + " is using M'aban, the Naalu Commander, to look at the top & bottom agenda, and their neighbour's promissory notes.");
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb.toString());
    }

    public static void sendTopAgendaToCardsInfoSkipCovert(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**Top Agenda:**__");
        String agendaID = game.lookAtTopAgenda(0);
        MessageEmbed embed = null;
        if (game.getSentAgendas().get(agendaID) != null) {
            if (game.getCurrentAgendaInfo().contains("_CL_") && game.getPhaseOfGame().startsWith("agenda")) {
                sb.append("You are currently voting on Covert Legislation and the top agenda is in the speaker's hand.");
                sb.append(" Showing the next agenda because that's how it should be by the RULEZ\n");
                agendaID = game.lookAtTopAgenda(1);

                if (game.getSentAgendas().get(agendaID) != null) {
                    embed = AgendaModel.agendaIsInSomeonesHandEmbed();
                } else if (agendaID != null) {
                    embed = Mapper.getAgenda(agendaID).getRepresentationEmbed();
                }
            } else {
                sb.append("The top agenda is currently in somebody's hand. As per the RULEZ, you should not be able to see the next agenda until they are finished deciding top/bottom/discard");
            }
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

    }
}
