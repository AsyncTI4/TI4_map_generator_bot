package ti4.commands.relic;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

public class RelicCommand implements Command {

    private final Collection<RelicSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.RELIC;
    }

    public String getActionDescription() {
        return "Relic";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (RelicSubcommandData subcommand : subcommandData) {
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

    private Collection<RelicSubcommandData> getSubcommands() {
        Collection<RelicSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new RelicInfo());
        subcommands.add(new DrawRelic());
        subcommands.add(new PurgeRelic());
        subcommands.add(new ExhaustRelic());
        subcommands.add(new RefreshRelic());
        subcommands.add(new DrawSpecificRelic());
        subcommands.add(new RelicLookAtTop());
        subcommands.add(new RelicSend());
        subcommands.add(new ShuffleRelicBack());
        subcommands.add(new ShowRemainingRelics());
        subcommands.add(new AddRelicBackIntoDeck());
        subcommands.add(new SendFragments());
        subcommands.add(new PurgeFragments());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}
