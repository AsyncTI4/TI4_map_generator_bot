package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class PlayerCommand implements Command {

    private  Collection<PlayerSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.PLAYER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (PlayerSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)){
                subcommand.execute(event);
            }
        }
    }


    protected String getActionDescription() {
        return "Player";
    }


    private Collection<PlayerSubcommandData> getSubcommands()
    {
        Collection<PlayerSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new Stats());
        subcommands.add(new Tech());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
