package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class RemoveEvent extends EventSubcommandData {
    public RemoveEvent() {
        super(Constants.REMOVE_EVENT, "Remove Event");
        addOptions(new OptionData(OptionType.STRING, Constants.EVENT_ID, "Event ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        String eventID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsString);
        if (Mapper.isValidEvent(eventID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Not a valid Event ID");
            return;
        }

        boolean success = activeGame.removeLaw(eventID);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event removed");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event ID not found");
        }
    }
}
