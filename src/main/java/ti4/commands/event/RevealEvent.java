package ti4.commands.event;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.model.EventModel;

public class RevealEvent extends EventSubcommandData {
    public RevealEvent() {
        super(Constants.REVEAL, "Reveal top Agenda from deck");
        addOption(OptionType.BOOLEAN, Constants.REVEAL_FROM_BOTTOM, "Reveal the agenda from the bottom of the deck instead of the top");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        boolean revealFromBottom =  event.getOption(Constants.REVEAL_FROM_BOTTOM, false, OptionMapping::getAsBoolean);
        revealEvent(event, activeGame, event.getChannel(), activeGame.revealEvent(revealFromBottom));
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
