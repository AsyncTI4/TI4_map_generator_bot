package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.cardspn.PNInfo;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

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
        sb.append(event.getUser().getAsMention()).append("\n");
        sb.append("__**Top Agenda:**__\n");
        String agendaID = activeGame.lookAtTopAgenda(0);
        sb.append("1: ");
        if (activeGame.getSentAgendas().get(agendaID) != null) {
            sb.append("This agenda is currently in somebody's hand. Showing the next agenda\n");
            agendaID = activeGame.lookAtTopAgenda(1);
            if (activeGame.getSentAgendas().get(agendaID) != null) {
                sb.append("This agenda is currently in somebody's hand.");
            } else if (agendaID != null) {
                sb.append(Helper.getAgendaRepresentation(agendaID));
            }
        } else if (agendaID != null) {
            sb.append(Helper.getAgendaRepresentation(agendaID));
        } else {
            sb.append("Could not find agenda");
        }
        sb.append("\n\n");
        sb.append("__**Bottom Agenda:**__\n");
        agendaID = activeGame.lookAtBottomAgenda(0);
        sb.append("1: ");
        if (activeGame.getSentAgendas().get(agendaID) != null) {
            sb.append("This agenda is currently in somebody's hand.");
        } else if (agendaID != null) {
            sb.append(Helper.getAgendaRepresentation(agendaID));
        } else {
            sb.append("Could not find agenda");
        }
        sb.append("\n\n");

        for (Player player_ : player.getNeighbouringPlayers()) {
            sb.append("_ _\n**__");
            sb.append(player_.getFactionEmoji());
            sb.append(Emojis.getColourEmojis(player_.getColor())).append(" ");
            sb.append(player_.getUserName()).append("'s Promissory Notes:__**\n");
            sb.append(PNInfo.getPromissoryNoteCardInfo(activeGame, player_, false));
        }

        if (!activeGame.isFoWMode()) MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
            player.getRepresentation() + " is using Naalu Commander to look at the top & bottom agenda, and their neighbour's promissory notes.");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, sb.toString());
    }
}
