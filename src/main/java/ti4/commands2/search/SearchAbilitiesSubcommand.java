package ti4.commands2.search;

import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.model.Source.ComponentSource;

public class SearchAbilitiesSubcommand extends SearchComponentModelSubcommand {

    public SearchAbilitiesSubcommand() {
        super(Constants.SEARCH_ABILITIES, "List all abilities");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidAbility(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getAbility(searchString).getRepresentationEmbed()).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getAbilities().values().stream()
            .filter(model -> model.search(searchString, source))
            .map(model -> model.getRepresentationEmbed(true))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
