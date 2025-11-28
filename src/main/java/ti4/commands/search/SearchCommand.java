package ti4.commands.search;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SearchCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    // TO DO:
                    // - keep in this command /search commands, sources, emojis, games, forgames, mygames, mytitles
                    // - create a new command /search_comps for all the rest

                    /* From \data\ */
                    new SearchAbilitiesSubcommand(),
                    new SearchActionCardsSubcommand(),
                    new SearchAgendasSubcommand(),
                    new SearchAttachmentsSubcommand(),
                    // no /search colors yet, but there is /help sample_colors
                    // no /search combat_modifiers yet
                    new SearchExploresSubcommand(),
                    // no /search franken_errata yet
                    // no /search generic_cards yet
                    new SearchLeadersSubcommand(),
                    new SearchGenomesSubcommand(),
                    new SearchParadigmsSubcommand(),
                    // no /search map_templates yet
                    new SearchPromissoryNotesSubcommand(),
                    new SearchPublicObjectivesSubcommand(),
                    new SearchRelicsSubcommand(),
                    new SearchSecretObjectivesSubcommand(),
                    // new SearchSources(),
                    new SearchTechsSubcommand(),
                    new SearchTokensSubcommand(),
                    new SearchUnitsSubcommand(),
                    new SearchPlanetsSubcommand(),
                    new SearchTilesSubcommand(),
                    new SearchBreakthroughs(),
                    new SearchRules(),
                    new SearchGalacticEventsSubcommand(),

                    /* From others */
                    // new SearchGames(),
                    new SearchForGame(),
                    new SearchMyGames())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.SEARCH;
    }

    @Override
    public String getDescription() {
        return "Search game component descriptions";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }

    private final Map<String, Subcommand> searchSubcommands = Stream.of(
                    new SearchAbilitiesSubcommand(),
                    new SearchPlanetsSubcommand(),
                    new SearchTilesSubcommand(),
                    new SearchUnitsSubcommand(),
                    new SearchAgendasSubcommand(),
                    new SearchSecretObjectivesSubcommand(),
                    new SearchPublicObjectivesSubcommand(),
                    new SearchRelicsSubcommand(),
                    new SearchActionCardsSubcommand(),
                    new SearchTechsSubcommand(),
                    new SearchLeadersSubcommand(),
                    new SearchGenomesSubcommand(),
                    new SearchParadigmsSubcommand(),
                    new SearchPromissoryNotesSubcommand(),
                    new SearchExploresSubcommand(),
                    new SearchDecksSubcommand(),
                    new SearchFactionsSubcommand(),
                    new SearchStrategyCardsSubcommand())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public Map<String, Subcommand> getSearchSubcommands() {
        return searchSubcommands;
    }
}
