package ti4.commands.combat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class CombatCommand implements Command {
    private final Collection<CombatSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.COMBAT;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionID(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        CombatSubcommandData executedCommand = null;
        for (CombatSubcommandData subcommand : subcommandData) {
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
        String userID = event.getUser().getId();
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);
    }

    protected String getActionDescription() {
        return "Combat";
    }

    private Collection<CombatSubcommandData> getSubcommands() {
        HashSet<CombatSubcommandData> hashSet = new HashSet<>();
        hashSet.add(new CombatRoll());
        hashSet.add(new StartCombat());
        return hashSet;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}
