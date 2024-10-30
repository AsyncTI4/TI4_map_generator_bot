package ti4.commands.agenda;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        Game game = getActiveGame();
        showDiscards(game, event);
    }

    public static void showDiscards(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb2 = new StringBuilder();
        String sb = "### __**Discarded Agendas:**__";
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        List<MessageEmbed> agendaEmbeds = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : discardAgendas.entrySet()) {
            agendaEmbeds.add(Mapper.getAgenda(entry.getKey()).getRepresentationEmbed());
            sb2.append(Mapper.getAgenda(entry.getKey()).getName()).append(" (ID: ").append(entry.getValue()).append(")\n");
        }
        MessageHelper.sendMessageToChannelWithEmbeds(event.getMessageChannel(), sb, agendaEmbeds);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb2.toString());
    }
}
