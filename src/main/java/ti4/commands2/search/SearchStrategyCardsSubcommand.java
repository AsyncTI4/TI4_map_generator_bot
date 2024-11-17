package ti4.commands2.search;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardModel;

public class SearchStrategyCardsSubcommand extends SearchComponentModelSubcommand {

    public SearchStrategyCardsSubcommand() {
        super(Constants.SEARCH_STRATEGY_CARDS, "List all strategy cards the bot can use");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (Mapper.isValidStrategyCard(searchString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getStrategyCard(searchString).getRepresentationEmbed(true)).queue();
            return;
        }

        List<MessageEmbed> messageEmbeds = Mapper.getStrategyCards().values().stream()
            .filter(model -> model.search(searchString, source))
            .sorted(Comparator.comparing(StrategyCardModel::getInitiative))
            .map(model -> model.getRepresentationEmbed(true))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
