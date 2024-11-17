package ti4.commands2.search;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;

class SearchLeadersSubcommand extends SearchComponentModelSubcommand {

    public SearchLeadersSubcommand() {
        super(Constants.SEARCH_LEADERS, "List all leaders the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidLeader(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getLeader(searchString).getRepresentationEmbed(true, true, true, true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getLeaders().values().stream()
            .filter(model -> model.search(searchString, source))
            .sorted(Comparator.comparing(LeaderModel::getID))
            .map(model -> model.getRepresentationEmbed(true, true, true, true))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
