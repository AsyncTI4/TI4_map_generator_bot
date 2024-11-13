package ti4.commands.search;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.ParentCommand;
import ti4.helpers.Constants;

public class SearchCommand implements ParentCommand {

    private final Collection<SearchSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.SEARCH;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (SearchSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                break;
            }
        }
    }

    public String getDescription() {
        return "Search game component descriptions";
    }

    private Collection<SearchSubcommandData> getSubcommands() {
        Collection<SearchSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new SearchAbilities());
        subcommands.add(new SearchGames());
        subcommands.add(new SearchPlanets());
        subcommands.add(new SearchTiles());
        subcommands.add(new SearchUnits());
        subcommands.add(new SearchCommands());
        subcommands.add(new SearchMyGames());
        subcommands.add(new SearchForGame());
        subcommands.add(new SearchMyTitles());
        subcommands.add(new SearchAgendas());
        subcommands.add(new SearchEvents());
        subcommands.add(new SearchSecretObjectives());
        subcommands.add(new SearchPublicObjectives());
        subcommands.add(new SearchRelics());
        subcommands.add(new SearchActionCards());
        subcommands.add(new SearchTechs());
        subcommands.add(new SearchLeaders());
        subcommands.add(new SearchPromissoryNotes());
        subcommands.add(new SearchExplores());
        subcommands.add(new SearchDecks());
        subcommands.add(new SearchFactions());
        subcommands.add(new SearchEmojis());
        subcommands.add(new SearchStrategyCards());

        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getName(), getDescription()).addSubcommands(getSubcommands()));
    }
}
