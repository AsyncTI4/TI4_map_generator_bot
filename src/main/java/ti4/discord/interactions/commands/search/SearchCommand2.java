package ti4.discord.interactions.commands.search;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class SearchCommand2 extends AbstractParentCommand {

    public SearchCommand2() {
        super(
                Constants.SEARCH + "2",
                "Search game component descriptions",
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
                new SearchMyTitles());
    }
}
