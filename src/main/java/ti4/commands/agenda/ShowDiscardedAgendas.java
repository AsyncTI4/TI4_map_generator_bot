package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class ShowDiscardedAgendas extends AgendaSubcommandData {
    public ShowDiscardedAgendas() {
        super(Constants.SHOW_DISCARDED, "Show discarded Agendas");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Discarded Agendas:**__\n");
        LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
        int index = 1;
        for (java.util.Map.Entry<String, Integer> entry : discardAgendas.entrySet()) {
            sb.append(index).append(". ").append(Helper.getAgendaRepresentation(entry.getKey(), entry.getValue()));
            index++;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
