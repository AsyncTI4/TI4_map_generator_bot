package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.collections4.CollectionUtils;

public class ListVoteCount extends AgendaSubcommandData {
    public ListVoteCount() {
        super(Constants.VOTE_COUNT, "List Vote count for agenda");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        turnOrder(event, activeMap);
    }

    public static void turnOrder(SlashCommandInteractionEvent event, Map activeMap) {
        turnOrder(event, activeMap, event.getChannel());
    }

    public static void turnOrder(GenericInteractionCreateEvent event, Map activeMap, MessageChannel channel) {
        List<Player> orderList = AgendaHelper.getVotingOrder(activeMap);
        StringBuilder sb = new StringBuilder("**__Vote Count:__**\n");
        int itemNo = 1;
        for (Player player : orderList) {
            sb.append("`").append(itemNo).append(".` ");
            sb.append(Helper.getPlayerRepresentation(player, activeMap));
            if (player.getUserID().equals(activeMap.getSpeaker())) sb.append(Emojis.SpeakerToken);
            sb.append(AgendaHelper.getPlayerVoteText(activeMap, player));
            sb.append("\n");
            itemNo++;
        }
        MessageHelper.sendMessageToChannel(channel, sb.toString());   
    }
}
