package ti4.commands.agenda;

import java.util.*;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowDiscardedAgendas extends AgendaSubcommandData {
    public ShowDiscardedAgendas() {
        super(Constants.SHOW_DISCARDED, "Show discarded Agendas");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        showDiscards(activeGame, event);
    }

    public void showDiscards(Game activeGame, GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("### __**Discarded Agendas:**__");
        Map<String, Integer> discardAgendas = activeGame.getDiscardAgendas();
        List<MessageEmbed> agendaEmbeds = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : discardAgendas.entrySet()) {
            agendaEmbeds.add(Mapper.getAgenda(entry.getKey()).getRepresentationEmbed());
        }
        MessageHelper.sendMessageToChannelWithEmbeds(event.getMessageChannel(), sb.toString(), agendaEmbeds);
    }
}
