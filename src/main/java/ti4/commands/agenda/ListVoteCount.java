package ti4.commands.agenda;

import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.fow.FOWOptions;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ListVoteCount extends GameStateSubcommand {

    public ListVoteCount() {
        super(Constants.VOTE_COUNT, "List Vote count for agenda", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        turnOrder(event, getGame());
    }

    public static void turnOrder(SlashCommandInteractionEvent event, Game game) {
        turnOrder(game, event.getChannel());
    }

    public static void turnOrder(Game game, MessageChannel channel) {
        List<Player> orderList = AgendaHelper.getVotingOrder(game);
        int votes = 0;
        for (Player player : orderList) {
            votes = votes + AgendaHelper.getTotalVoteCount(game, player);
        }
        StringBuilder sb = new StringBuilder("**__Vote Count (Total votes: "
            + (Boolean.parseBoolean(game.getFowOption(FOWOptions.HIDE_TOTAL_VOTES)) ? "???" : votes));
        sb.append("):__**\n");
        int itemNo = 1;
        for (Player player : orderList) {
            sb.append("`").append(itemNo).append(".` ");
            sb.append(player.getRepresentation(false, false));
            if (player.getUserID().equals(game.getSpeakerUserID())) sb.append(Emojis.SpeakerToken);
            sb.append(AgendaHelper.getPlayerVoteText(game, player));
            sb.append("\n");
            itemNo++;
        }
        MessageHelper.sendMessageToChannel(channel, sb.toString());
    }
}
