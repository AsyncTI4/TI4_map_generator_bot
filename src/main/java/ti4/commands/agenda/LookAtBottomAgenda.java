package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LookAtBottomAgenda extends AgendaSubcommandData {
    public LookAtBottomAgenda() {
        super(Constants.LOOK_AT_BOTTOM, "Look at bottom Agenda from deck");
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of agendas to look at");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        int count = 1;
        OptionMapping countOption = event.getOption(Constants.COUNT);
        if (countOption != null) {
            int providedCount = countOption.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-----------\n");
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("`").append(event.getCommandString()).append("`").append("\n");
        if (count > 1) {
            sb.append("__**Bottom " + count + " agendas:**__\n");
        } else {
            sb.append("__**Bottom agenda:**__\n");
        }
        for (int i = 0; i < count; i++) {
            String agendaID = activeMap.lookAtBottomAgenda(i);
            sb.append((i+1) + ": ");
            if (activeMap.getSentAgendas().get(agendaID) != null) {
                sb.append("This agenda is currently in somebody's hand.");
            } else {
                sb.append(Helper.getAgendaRepresentation(agendaID));
            }
            sb.append("\n");
        }
        sb.append("-----------\n");

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
