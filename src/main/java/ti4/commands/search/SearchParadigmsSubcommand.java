package ti4.commands.search;

import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.logging.BotLogger;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;

class SearchParadigmsSubcommand extends SearchComponentModelSubcommand {

    public SearchParadigmsSubcommand() {
        super(Constants.SEARCH_PARADIGMS, "List all paradigms (Twilight's Fall heroes) the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidLeader(searchString)) {
            LeaderModel leaderModel = Mapper.getLeader(searchString);
            if (leaderModel.isParadigm()) {
                event.getChannel()
                        .sendMessageEmbeds(leaderModel.getRepresentationEmbed(true, true, false, true, true))
                        .queue(Consumers.nop(), BotLogger::catchRestError);
                return;
            }
        }
        List<MessageEmbed> messageEmbeds = Mapper.getDeck(Constants.TF_PARADIGM).getNewDeck().stream()
                .map(Mapper::getLeader)
                .filter(model -> model.search(searchString, source))
                .sorted(Comparator.comparing(LeaderModel::getID))
                .map(model -> model.getRepresentationEmbed(true, true, false, true, true))
                .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
