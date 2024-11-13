package ti4.commands.search;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SearchCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new SearchAbilities(),
            new SearchGames(),
            new SearchPlanets(),
            new SearchTiles(),
            new SearchUnits(),
            new SearchCommands(),
            new SearchMyGames(),
            new SearchForGame(),
            new SearchMyTitles(),
            new SearchAgendas(),
            new SearchEvents(),
            new SearchSecretObjectives(),
            new SearchPublicObjectives(),
            new SearchRelics(),
            new SearchActionCards(),
            new SearchTechs(),
            new SearchLeaders(),
            new SearchPromissoryNotes(),
            new SearchExplores(),
            new SearchDecks(),
            new SearchFactions(),
            new SearchEmojis(),
            new SearchStrategyCards()
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
