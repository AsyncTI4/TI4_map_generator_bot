package ti4.commands2.agenda;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

public class LookAtTopAgenda extends AgendaSubcommandData {
    public LookAtTopAgenda() {
        super(Constants.LOOK_AT_TOP, "Look at top Agenda from deck");
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of agendas to look at");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        int count = 1;
        OptionMapping countOption = event.getOption(Constants.COUNT);
        if (countOption != null) {
            int providedCount = countOption.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }

        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "You are not a player in this game.");
            return;
        }

        lookAtAgendas(game, player, count, false);
    }

    @ButtonHandler("agendaLookAt") // agendaLookAt[count:X][lookAtBottom:Y] where X = int and Y = boolean
    public static void lookAtAgendas(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int count = Integer.parseInt(StringUtils.substringBetween(buttonID, "[count:","]"));
        boolean lookAtBottom = Boolean.parseBoolean(StringUtils.substringBetween(buttonID, "[lookAtBottom:","]"));
        lookAtAgendas(game, player, count, lookAtBottom);
        ButtonHelper.deleteMessage(event);
    }

    public static void lookAtAgendas(Game game, Player player, int count, boolean lookFromBottom) {
        String sb = player.getRepresentationUnfogged() + " here " + (count == 1 ? "is" : "are") + " the agenda" + (count == 1 ? "" : "s") + " you have looked at:";
        List<MessageEmbed> agendaEmbeds = getAgendaEmbeds(count, lookFromBottom, game);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, sb, agendaEmbeds);
    }

    public static List<MessageEmbed> getAgendaEmbeds(int count, boolean fromBottom, Game game) {
        List<MessageEmbed> agendaEmbeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String agendaID = fromBottom ? game.lookAtBottomAgenda(i) : game.lookAtTopAgenda(i);
            if (agendaID != null) {
                AgendaModel agenda = Mapper.getAgenda(agendaID);
                if (game.getSentAgendas().get(agendaID) != null) {
                    agendaEmbeds.add(AgendaModel.agendaIsInSomeonesHandEmbed());
                } else {
                    agendaEmbeds.add(agenda.getRepresentationEmbed());
                }
            }
        }
        return agendaEmbeds;
    }

}
