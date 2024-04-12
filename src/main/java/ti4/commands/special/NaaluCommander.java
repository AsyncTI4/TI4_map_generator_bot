package ti4.commands.special;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.cardspn.PNInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
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
        Game activeGame = getActiveGame();

        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        secondHalfOfNaaluCommander(event, activeGame, player);
    }

    public void secondHalfOfNaaluCommander(GenericInteractionCreateEvent event, Game activeGame, Player player) {

        if (!activeGame.playerHasLeaderUnlockedOrAlliance(player, "naalucommander")) {
            sendMessage("Only players with access to an unlocked Naalu Commander can use this ability");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentation(true, true)).append(" you are using the Naalu Commander:");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, sb.toString());

        // Top Agenda
        sendTopAgendaToCardsInfoSkipCovert(activeGame, player);

        // Bottom Agenda
        MessageEmbed embed = null;
        sb.setLength(0);
        sb.append("__**Bottom Agenda:**__\n");
        String agendaID = activeGame.lookAtBottomAgenda(0);
        if (activeGame.getSentAgendas().get(agendaID) != null) {
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
            sb.append(PNInfo.getPromissoryNoteCardInfo(activeGame, player_, false, true));
        }

        if (!activeGame.isFoWMode()) MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
            player.getRepresentation() + " is using Naalu Commander to look at the top & bottom agenda, and their neighbour's promissory notes.");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, sb.toString());
    }

    public static void sendTopAgendaToCardsInfoSkipCovert(Game activeGame, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**Top Agenda:**__");
        String agendaID = activeGame.lookAtTopAgenda(0);
        MessageEmbed embed = null;
        if (activeGame.getSentAgendas().get(agendaID) != null) {
            if (activeGame.getCurrentAgendaInfo().contains("_CL_") && activeGame.getCurrentPhase().startsWith("agenda")) {
                sb.append("You are currently voting on covert legislation and the top agenda is in the speaker's hand.");
                sb.append(" Showing the next agenda because thats how it should be by the RULEZ\n");
                agendaID = activeGame.lookAtTopAgenda(1);

                if (activeGame.getSentAgendas().get(agendaID) != null) {
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
        MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), sb.toString(), embed);
    }
}
