package ti4.commands2.search;

import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;

public class SearchSecretObjectivesSubcommand extends SearchComponentModelSubcommand {

    public SearchSecretObjectivesSubcommand() {
        super(Constants.SEARCH_SECRET_OBJECTIVES, "List all secret objectives the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidSecretObjective(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getSecretObjective(searchString).getRepresentationEmbed(true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getSecretObjectives().values().stream()
            .sorted(SecretObjectiveModel.sortByPointsAndName)
            .filter(model -> model.search(searchString, source))
            .map(model -> model.getRepresentationEmbed(true))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
