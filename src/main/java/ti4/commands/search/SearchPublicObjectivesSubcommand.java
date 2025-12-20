package ti4.commands.search;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.logging.BotLogger;
import ti4.model.PublicObjectiveModel;
import ti4.model.Source.ComponentSource;

class SearchPublicObjectivesSubcommand extends SearchComponentModelSubcommand {

    public SearchPublicObjectivesSubcommand() {
        super(Constants.SEARCH_PUBLIC_OBJECTIVES, "List all public objectives the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidPublicObjective(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(Mapper.getPublicObjective(searchString).getRepresentationEmbed(true))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getPublicObjectives().values().stream()
                .filter(model -> model.search(searchString, source))
                .sorted(PublicObjectiveModel.sortByPointsAndName)
                .map(model -> model.getRepresentationEmbed(true))
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
