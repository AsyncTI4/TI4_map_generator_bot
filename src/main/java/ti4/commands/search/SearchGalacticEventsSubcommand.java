package ti4.commands.search;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;

class SearchGalacticEventsSubcommand extends SearchComponentModelSubcommand {

    public SearchGalacticEventsSubcommand() {
        super(Constants.SEARCH_GALACTIC_EVENTS, "List all galactic events the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidGalacticEvent(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(Mapper.getGalacticEvent(searchString).getRepresentationEmbed(true))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getGalacticEvents().values().stream()
                .filter(model -> model.search(searchString, source))
                .map(model -> model.getRepresentationEmbed(true))
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
