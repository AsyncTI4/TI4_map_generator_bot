package ti4.commands2.event;

import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

class ShowDiscardedEvents extends GameStateSubcommand {

    public ShowDiscardedEvents() {
        super(Constants.SHOW_DISCARDED, "Show discarded Events", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**Discarded Events:**__\n");
        int index = 1;
        for (Entry<String, Integer> eventEntry : getGame().getDiscardedEvents().entrySet()) {
            EventModel eventModel = Mapper.getEvent(eventEntry.getKey());
            if (eventModel == null) continue;
            sb.append(index).append(". ").append(eventModel.getRepresentation(eventEntry.getValue()));
            index++;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
