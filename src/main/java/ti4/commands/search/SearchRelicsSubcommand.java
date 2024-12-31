package ti4.commands.search;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.model.RelicModel;
import ti4.model.Source.ComponentSource;

class SearchRelicsSubcommand extends SearchComponentModelSubcommand {

    public SearchRelicsSubcommand() {
        super(Constants.SEARCH_RELICS, "List all relics the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidRelic(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getRelic(searchString).getRepresentationEmbed(true, true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getRelics().values().stream()
            .filter(model -> model.search(searchString, source))
            .sorted(Comparator.comparing(RelicModel::getName))
            .map(model -> model.getRepresentationEmbed(true, true))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
