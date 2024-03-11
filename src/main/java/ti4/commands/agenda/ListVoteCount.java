package ti4.commands.agenda;

import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListVoteCount extends AgendaSubcommandData {
    public ListVoteCount() {
        super(Constants.VOTE_COUNT, "List Vote count for agenda");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        turnOrder(event, activeGame);
    }

    public static void turnOrder(SlashCommandInteractionEvent event, Game activeGame) {
        turnOrder(event, activeGame, event.getChannel());
    }

    public static void turnOrder(GenericInteractionCreateEvent event, Game activeGame, MessageChannel channel) {
        List<Player> orderList = AgendaHelper.getVotingOrder(activeGame);
        StringBuilder sb = new StringBuilder("**__Vote Count:__**\n");
        int itemNo = 1;
        for (Player player : orderList) {
            sb.append("`").append(itemNo).append(".` ");
            sb.append(player.getRepresentation());
            if (player.getUserID().equals(activeGame.getSpeaker())) sb.append(Emojis.SpeakerToken);
            sb.append(AgendaHelper.getPlayerVoteText(activeGame, player));
            sb.append("\n");
            itemNo++;
        }
        MessageHelper.sendMessageToChannel(channel, sb.toString());   
    }
}
