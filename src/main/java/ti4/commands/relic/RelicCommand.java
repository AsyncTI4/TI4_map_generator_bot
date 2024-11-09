package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

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
        Game game = GameManager.getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);
    }

    private Collection<RelicSubcommandData> getSubcommands() {
        Collection<RelicSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new RelicInfo());
        subcommands.add(new RelicDraw());
        subcommands.add(new RelicPurge());
        subcommands.add(new RelicExhaust());
        subcommands.add(new RelicReady());
        subcommands.add(new RelicDrawSpecific());
        subcommands.add(new RelicLookAtTop());
        subcommands.add(new RelicSend());
        subcommands.add(new RelicShuffleBack());
        subcommands.add(new RelicShowRemaining());
        subcommands.add(new RelicAddBackIntoDeck());
        subcommands.add(new RelicSendFragments());
        subcommands.add(new RelicPurgeFragments());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}
