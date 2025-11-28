package ti4.commands.search;

import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;

class SearchGenomesSubcommand extends SearchComponentModelSubcommand {

    public SearchGenomesSubcommand() {
        super(Constants.SEARCH_GENOMES, "List all genomes (Twilight's Fall agents) the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidLeader(searchString)) {
            LeaderModel leaderModel = Mapper.getLeader(searchString);
            if (Constants.AGENT.equalsIgnoreCase(leaderModel.getType())
                    && (leaderModel.getSource() == ComponentSource.twilights_fall
                            || leaderModel.getTFName().isPresent()
                            || leaderModel.getTFAbilityText().isPresent()
                            || leaderModel.getTFAbilityWindow().isPresent()
                            || leaderModel.getTFTitle().isPresent())) {
                event.getChannel()
                        .sendMessageEmbeds(leaderModel.getRepresentationEmbed(true, true, false, true, true))
                        .queue();
                return;
            }
        }
        List<MessageEmbed> messageEmbeds = Mapper.getLeaders().values().stream()
                .filter(model -> Constants.AGENT.equalsIgnoreCase(model.getType()))
                .filter(model -> model.getSource() == ComponentSource.twilights_fall
                        || model.getTFName().isPresent()
                        || model.getTFAbilityText().isPresent()
                        || model.getTFAbilityWindow().isPresent()
                        || model.getTFTitle().isPresent())
                .filter(model -> model.search(searchString, source))
                .sorted(Comparator.comparing(LeaderModel::getID))
                .map(model -> model.getRepresentationEmbed(true, true, false, true, true))
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
