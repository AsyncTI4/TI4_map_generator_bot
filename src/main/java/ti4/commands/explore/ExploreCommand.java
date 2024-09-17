package ti4.commands.explore;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.relic.RelicPurgeFragments;
import ti4.commands.relic.RelicSendFragments;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

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
                break;
            }
        }
        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(game, event);
    }

    private Collection<ExploreSubcommandData> getSubcommands() {
        Collection<ExploreSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DiscardFromDeckExp());
        subcommands.add(new ShuffleIntoDeckFromHandExp());
        subcommands.add(new ExploreAndDiscard());
        subcommands.add(new RemoveExplore());
        subcommands.add(new ShuffleExpBackIntoDeck());
        subcommands.add(new ExpInfo());
        subcommands.add(new ExpPlanet());
        subcommands.add(new ExpReset());
        subcommands.add(new ExpFrontier());
        subcommands.add(new UseExplore());
        subcommands.add(new ExploreLookAtTop());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
