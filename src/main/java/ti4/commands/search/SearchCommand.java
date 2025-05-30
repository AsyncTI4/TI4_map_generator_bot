package ti4.commands.search;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SearchCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
    
        new SearchCommandsSubcommand(),

        // From \data\
        new SearchAbilitiesSubcommand(),
        new SearchActionCardsSubcommand(),
        new SearchAgendasSubcommand(),
        new SearchAttachmentsSubcommand(),
        // no /search colors yet, but there is /help sample_colors
        // no /search combat_modifiers yet
        new SearchDecksSubcommand(),
        new SearchEventsSubcommand(),
        new SearchExploresSubcommand(),
        new SearchFactionsSubcommand(),
        // no /search franken_errata yet
        // no /search generic_cards yet
        new SearchLeadersSubcommand(),
        // no /search map_templates yet
        new SearchPromissoryNotesSubcommand(),
        new SearchPublicObjectivesSubcommand(),
        new SearchRelicsSubcommand(),
        new SearchSecretObjectivesSubcommand(),
        new SearchSources(),
        // no /search strategy_card_sets yet
        new SearchStrategyCardsSubcommand(),
        new SearchTechsSubcommand(),
        new SearchTokensSubcommand(),
        new SearchUnitsSubcommand(),

        // From \resources\
        new SearchEmojis(),
        new SearchPlanetsSubcommand(),
        new SearchTilesSubcommand(),

        // Others
        //new SearchGames(),
        new SearchMyGames(),
        new SearchForGame(),
        new SearchMyTitles()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

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
        new SearchPromissoryNotesSubcommand(),
        new SearchExploresSubcommand(),
        new SearchDecksSubcommand(),
        new SearchFactionsSubcommand(),
        new SearchStrategyCardsSubcommand()).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public Map<String, Subcommand> getSearchSubcommands() {
        return searchSubcommands;
    }
}
