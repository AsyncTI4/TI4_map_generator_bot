package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class LookAtBottomAgenda extends AgendaSubcommandData {
    public LookAtBottomAgenda() {
        super(Constants.LOOK_AT_BOTTOM, "Look at bottom Agenda from deck");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        StringBuilder sb = new StringBuilder();
        sb.append("-----------\n");
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Agenda Bottom:\n");
        sb.append(Mapper.getAgenda(activeMap.lookAtBottomAgenda()));
        sb.append("\n-----------\n");
        MessageHelper.sentToMessageToUser(event, sb.toString());
    }
}
