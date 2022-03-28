package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class DrawAgenda extends AgendaSubcommandData {
    public DrawAgenda() {
        super(Constants.DRAW, "Draw Agenda");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }
        Map activeMap = getActiveMap();
        StringBuilder sb = new StringBuilder();
        sb.append("-----------\n");
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Agendas:\n");
        int index = 1;
        for (int i = 0; i < count; i++) {
            java.util.Map.Entry<String, Integer> entry = activeMap.drawAgenda();
            if (entry != null) {
                sb.append(index).append(". (").append(entry.getValue()).append(") ").append(Mapper.getAgenda(entry.getKey()));
                index++;
                sb.append("\n");
            }
        }
        sb.append("-----------\n");
        MessageHelper.sentToMessageToUser(event, sb.toString());
    }
}
