package ti4.commands2.search;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class SearchCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new SearchAbilitiesSubcommand(),
            new SearchGames(),
            new SearchPlanetsSubcommand(),
            new SearchTilesSubcommand(),
            new SearchUnitsSubcommand(),
            new SearchCommands(),
            new SearchMyGames(),
            new SearchForGame(),
            new SearchMyTitles(),
            new SearchAgendasSubcommand(),
            new SearchEventsSubcommand(),
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
            new SearchEmojisSubcommand(),
            new SearchStrategyCardsSubcommand(),
            new SearchSources()
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
}
