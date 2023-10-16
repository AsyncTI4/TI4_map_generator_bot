package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.message.MessageHelper;

import java.util.List;

public class ShowDiscardedEvents extends EventSubcommandData {
    public ShowDiscardedEvents() {
        super(Constants.SHOW_DISCARDED, "Show discarded Events");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Discarded Events:**__\n");
        List<String> discardEvents = activeGame.getDiscardedEvents();
        int index = 1;
        for (String eventID : discardEvents) {
            sb.append(index).append(". ").append(Helper.getAgendaRepresentation(eventID));
            index++;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
