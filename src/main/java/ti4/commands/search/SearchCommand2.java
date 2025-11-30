package ti4.commands.search;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SearchCommand2 implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    // TO DO:
                    // - keep in this command /search commands, sources, emojis, games, forgames, mygames, mytitles
                    // - create a new command /search_comps for all the rest

                    new SearchCommandsSubcommand(),
                    new SearchDecksSubcommand(),
                    new SearchEventsSubcommand(),
                    new SearchFactionsSubcommand(),
                    // no /search franken_errata yet
                    // no /search generic_cards yet
                    // no /search map_templates yet
                    new SearchSources(),
                    new SearchStrategyCardsSubcommand(),

                    /* From \resources\ */
                    new SearchEmojis(),
                    new SearchMyTitles())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.SEARCH + "2";
    }

    @Override
    public String getDescription() {
        return "Search game component descriptions";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
