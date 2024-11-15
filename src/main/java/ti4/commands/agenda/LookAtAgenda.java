package ti4.commands.agenda;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

class LookAtAgenda extends GameStateSubcommand {

    public LookAtAgenda() {
        super(Constants.LOOK, "Look at the agenda deck", false, true);
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of agendas to look at");
        addOption(OptionType.BOOLEAN, Constants.LOOK_AT_BOTTOM, "To look at top or bottom");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);
        boolean lookAtBottom = event.getOption(Constants.LOOK_AT_BOTTOM, false, OptionMapping::getAsBoolean);

        Game game = getGame();
        Player player = getPlayer();
        lookAtAgendas(game, player, count, lookAtBottom);
    }

    private static void lookAtAgendas(Game game, Player player, int count, boolean lookFromBottom) {
        String sb = player.getRepresentationUnfogged() + " here " + (count == 1 ? "is" : "are") + " the agenda" + (count == 1 ? "" : "s") + " you have looked at:";
        List<MessageEmbed> agendaEmbeds = getAgendaEmbeds(count, lookFromBottom, game);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, sb, agendaEmbeds);
    }

    private static List<MessageEmbed> getAgendaEmbeds(int count, boolean fromBottom, Game game) {
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

    @ButtonHandler("agendaLookAt") // agendaLookAt[count:X][lookAtBottom:Y] where X = int and Y = boolean
    public static void lookAtAgendas(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int count = Integer.parseInt(StringUtils.substringBetween(buttonID, "[count:","]"));
        boolean lookAtBottom = Boolean.parseBoolean(StringUtils.substringBetween(buttonID, "[lookAtBottom:","]"));
        lookAtAgendas(game, player, count, lookAtBottom);
        ButtonHelper.deleteMessage(event);
    }
}
