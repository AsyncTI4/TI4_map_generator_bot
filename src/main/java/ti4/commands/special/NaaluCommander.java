package ti4.commands.special;

import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.cardspn.PNInfo;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class NaaluCommander extends SpecialSubcommandData {

    public NaaluCommander() {
        super(Constants.NAALU_COMMANDER, "Look at your neighbours' promissory notes and the top and bottom of the agenda deck.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        if (!activeMap.isTestBetaFeaturesMode()) {
            sendMessage("BETA TEST GAMES ONLY");
            return;
        }

        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        if (!player.getFaction().equals("naalu")) { //TODO: switch logic from isNaalu to hasNaaluCommander
            sendMessage("Only a Naalu player can use this ability");
            return;
        }

        if (player.getLeaderByType("commander").isLocked()) {
            sendMessage("Your commander is locked.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(event.getUser().getAsMention()).append("\n");
        sb.append("`").append(event.getCommandString()).append("`").append("\n");
        sb.append("__**Top Agenda:**__\n");
        String agendaID = activeMap.lookAtTopAgenda(0);
        sb.append("1: ");
        if (activeMap.getSentAgendas().get(agendaID) != null) {
            sb.append("This agenda is currently in somebody's hand.");
        } else {
            sb.append(Helper.getAgendaRepresentation(agendaID));
        }
        sb.append("\n\n");
        sb.append("__**Bottom Agenda:**__\n");
        agendaID = activeMap.lookAtBottomAgenda(0);
        sb.append("1: ");
        if (activeMap.getSentAgendas().get(agendaID) != null) {
            sb.append("This agenda is currently in somebody's hand.");
        } else {
            sb.append(Helper.getAgendaRepresentation(agendaID));
        }
        sb.append("\n\n");

        Set<Player> neighbours = Helper.getNeighbouringPlayers(activeMap, player);

        for (Player player_ : neighbours) {
            sb.append("_ _\n**__");
            sb.append(Helper.getFactionIconFromDiscord(player_.getFaction()));
            sb.append(Helper.getColourAsMention(event.getGuild(), player_.getColor())).append(" ");
            sb.append(player_.getUserName()).append("'s Promissory Notes:__**\n");
            sb.append(PNInfo.getPromissoryNoteCardInfo(activeMap, player_, false));
        }

        if (!activeMap.isFoWMode()) MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), Helper.getPlayerRepresentation(event, player) + " is using Naalu Commander to look at the top & bottom agenda, and their neighbour's promissory notes.");
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, sb.toString());
    }
}
