package ti4.commands.search;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class SearchCommand implements Command {

    private final Collection<SearchSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.SEARCH;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        SearchSubcommandData executedCommand = null;
        for (SearchSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }
        if (executedCommand == null) {
            reply(event);
        } else {
            executedCommand.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
    }

    protected String getActionDescription() {
        return "Search game component descriptions";
    }

    private Collection<SearchSubcommandData> getSubcommands() {
        Collection<SearchSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new ListAbilities());
        subcommands.add(new ListGames());
        subcommands.add(new ListPlanets());
        subcommands.add(new ListTiles());
        subcommands.add(new ListUnits());
        subcommands.add(new ListCommands());
        subcommands.add(new ListMyGames());
        subcommands.add(new ListAgendas());
        subcommands.add(new ListSecretObjectives());
        subcommands.add(new ListPublicObjectives());
        subcommands.add(new ListRelics());
        subcommands.add(new ListActionCards());
        subcommands.add(new ListTechs());
        subcommands.add(new ListLeaders());
        subcommands.add(new ListPromissoryNotes());
        subcommands.add(new ListExplores());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
