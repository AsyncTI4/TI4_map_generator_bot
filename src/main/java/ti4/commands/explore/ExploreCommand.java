package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class ExploreCommand implements Command {

    private final Collection<ExploreSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.EXPLORE;
    }

    public String getActionDescription() {
        return "Explore";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (ExploreSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
            }
        }
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap);
    }

    private Collection<ExploreSubcommandData> getSubcommands() {
        Collection<ExploreSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DiscardExp());
        subcommands.add(new ExpDeck());
        subcommands.add(new RemoveExplore());
        subcommands.add(new ShuffleExpBackIntoDeck());
        subcommands.add(new ExpInfo());
        subcommands.add(new ExpPlanet());
        subcommands.add(new ExpReset());
        subcommands.add(new ExpFrontier());
        subcommands.add(new SendFragments());
        subcommands.add(new UseExplore());
        subcommands.add(new PurgeFragments());
        subcommands.add(new ListFragments());
        subcommands.add(new DrawRelic());
        subcommands.add(new PurgeRelic());
        subcommands.add(new ShuffleRelicBack());
        subcommands.add(new ExhaustRelic());
        subcommands.add(new RefreshRelic());
        subcommands.add(new DrawSpecificRelic());
        subcommands.add(new ShowRemainingRelics());
        subcommands.add(new AddRelicBackIntoDeck());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
