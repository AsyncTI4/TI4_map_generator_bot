package ti4.commands.search;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.EventModel;
import ti4.model.Source.ComponentSource;

public class SearchEvents extends SearchComponentModel {

    public SearchEvents() {
        super(Constants.SEARCH_EVENTS, "List all events the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidEvent(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getEvent(searchString).getRepresentationEmbed(true, null)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (EventModel model : Mapper.getEvents().values()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed(true, null);
            if (Helper.embedContainsSearchTerm(representationEmbed, searchString)) messageEmbeds.add(representationEmbed);
        }
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
