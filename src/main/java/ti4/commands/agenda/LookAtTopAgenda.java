package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LookAtTopAgenda extends AgendaSubcommandData {
    public LookAtTopAgenda() {
        super(Constants.LOOK_AT_TOP, "Look at top Agenda from deck");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        StringBuilder sb = new StringBuilder();
        sb.append("-----------\n");
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Agenda Top:\n");
        sb.append(Mapper.getAgenda(activeMap.lookAtTopAgenda()));
        sb.append("\n-----------\n");

        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null){
            MessageHelper.sendMessageToUser(sb.toString(), event);
        } else {
            User userById = event.getJDA().getUserById(player.getUserID());
            if (userById != null) {
                if (activeMap.isCommunityMode() && player.getPrivateChannel() instanceof MessageChannel) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player.getPrivateChannel(), sb.toString());
                } else {
                    MessageHelper.sendMessageToUser(sb.toString(), userById);
                }
            } else {
                MessageHelper.sendMessageToUser(sb.toString(), event);
            }
        }
    }
}
