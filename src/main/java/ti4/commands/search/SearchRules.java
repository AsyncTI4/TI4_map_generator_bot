package ti4.commands.search;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;

class SearchRules extends SearchComponentModelSubcommand {

    public SearchRules() {
        super(Constants.SEARCH_RULES, "List various rules sections from the LRR or other homebrew sources.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        // single rule
        if (Mapper.isValidRule(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(Mapper.getRule(searchString).getRepresentationEmbed())
                    .queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getRules().values().stream()
                .filter(model -> model.search(searchString, source))
                .map(model -> model.getRepresentationEmbed())
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
