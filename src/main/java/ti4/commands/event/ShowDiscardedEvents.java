package ti4.commands.event;

import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

public class ShowDiscardedEvents extends EventSubcommandData {
    public ShowDiscardedEvents() {
        super(Constants.SHOW_DISCARDED, "Show discarded Events");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Discarded Events:**__\n");
        int index = 1;
        for (Entry<String, Integer> eventEntry : game.getDiscardedEvents().entrySet()) {
            EventModel eventModel = Mapper.getEvent(eventEntry.getKey());
            if (eventModel == null) continue;
            sb.append(index).append(". ").append(eventModel.getRepresentation(eventEntry.getValue()));
            index++;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
