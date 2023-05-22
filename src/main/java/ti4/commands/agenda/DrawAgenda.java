package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawAgenda extends AgendaSubcommandData {
    public DrawAgenda() {
        super(Constants.DRAW, "Draw Agenda");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }


    public void drawAgenda(GenericInteractionCreateEvent event, int count, Map activeMap, Player player) {


        StringBuilder sb = new StringBuilder();
        sb.append("-----------\n");
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append(event.getUser().getAsMention()).append("\n");
        sb.append("Drawn Agendas:\n");
        int index = 1;
        List<Button> buttons = new ArrayList<Button>();
        for (int i = 0; i < count; i++) {
            java.util.Map.Entry<String, Integer> entry = activeMap.drawAgenda();
            if (entry != null) {
                sb.append(index).append(". ").append(Helper.getAgendaRepresentation(entry.getKey(), entry.getValue()));
                index++;
                sb.append("\n");
                Button top = Button.primary("topAgenda_"+entry.getValue(), "Put agenda "+entry.getValue()+" on the top of the agenda deck.");
                Button bottom = Button.danger("bottomAgenda_"+entry.getValue(), "Put agenda "+entry.getValue()+" on the bottom of the agenda deck.");
                buttons.add(top);
                buttons.add(bottom);
            }
        }
        sb.append("-----------\n");

        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null){
            MessageHelper.sendMessageToUser(sb.toString(), event);
        } else {
            User userById = event.getJDA().getUserById(player.getUserID());
            if (userById != null) {
                if (activeMap.isCommunityMode() && player.getPrivateChannel() instanceof MessageChannel) {
                   // MessageHelper.sendMessageToChannel((MessageChannel) player.getPrivateChannel(), sb.toString());

                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player.getCardsInfoThread(activeMap), sb.toString(), buttons);
                } else {
                    //MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, sb.toString());
                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player.getCardsInfoThread(activeMap), sb.toString(), buttons);
                }
            } else {
                MessageHelper.sendMessageToUser(sb.toString(), event);
            }
        }
    }



    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        drawAgenda((GenericInteractionCreateEvent) event, count,activeMap, player);
    }
}

