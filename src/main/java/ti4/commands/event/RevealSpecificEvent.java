package ti4.commands.event;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

public class RevealSpecificEvent extends EventSubcommandData {
    public RevealSpecificEvent() {
        super(Constants.REVEAL_SPECIFIC, "Reveal top Event from deck");
        addOptions(new OptionData(OptionType.STRING, Constants.EVENT_ID, "Event ID (text ID found in /search events)").setRequired(true).setAutoComplete(true));
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force reveal the Event (even if it's not in the deck)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        String eventID = event.getOption(Constants.EVENT_ID, "", OptionMapping::getAsString);
        if (!Mapper.isValidEvent(eventID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Event ID found, please retry");
            return;
        }

        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!activeGame.revealEvent(eventID, force)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Event not found in deck, please retry");
            return;
        }

        revealEvent(event, activeGame, event.getChannel(), eventID);
    }

    public void revealEvent(GenericInteractionCreateEvent event, Game activeGame, MessageChannel channel, String eventID) {
        EventModel eventModel = Mapper.getEvent(eventID);
        if (eventModel != null) {
            channel.sendMessageEmbeds(eventModel.getRepresentationEmbed()).queue();
        } else {
            sendMessage("Something went wrong");
        }
    }
}
