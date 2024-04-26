package ti4.commands.agenda;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
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
        Game activeGame = getActiveGame();

        int count = 1;
        OptionMapping countOption = event.getOption(Constants.COUNT);
        if (countOption != null) {
            int providedCount = countOption.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }

        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "You are not a player in this game.");
            return;
        }

        lookAtAgendas(activeGame, player, count, false);
    }

    public static void lookAtAgendas(Game activeGame, Player player, int count, boolean lookFromBottom) {
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentation(true, true)).append(" here are the agenda(s) you have looked at:");
        List<MessageEmbed> agendaEmbeds = getAgendaEmbeds(count, lookFromBottom, activeGame);

        Player realPlayer = Helper.getGamePlayer(activeGame, player, (Member) null, null);
        if (realPlayer != null) {
            MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, realPlayer, sb.toString(), agendaEmbeds);
        }
    }

    public static List<MessageEmbed> getAgendaEmbeds(int count, boolean fromBottom, Game activeGame) {
        List<MessageEmbed> agendaEmbeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String agendaID = fromBottom ? activeGame.lookAtBottomAgenda(i) : activeGame.lookAtTopAgenda(i);
            if (agendaID != null) {
                AgendaModel agenda = Mapper.getAgenda(agendaID);
                if (activeGame.getSentAgendas().get(agendaID) != null) {
                    agendaEmbeds.add(AgendaModel.agendaIsInSomeonesHandEmbed());
                } else {
                    agendaEmbeds.add(agenda.getRepresentationEmbed());
                }
            }
        }
        return agendaEmbeds;
    }

}
