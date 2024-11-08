package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class AdminCommand implements Command {

    private final Collection<AdminSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.ADMIN;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfHasRole(getActionID(), event, AsyncTI4DiscordBot.adminRoles);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (AdminSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                break;
            }
        }
    }

    protected String getActionDescription() {
        return "Admin";
    }

    private Collection<AdminSubcommandData> getSubcommands() {
        Collection<AdminSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new DeleteGame());
        subcommands.add(new SaveMap());
        subcommands.add(new ResetEmojiCache());
        subcommands.add(new ReloadMap());
        subcommands.add(new ReloadMapperObjects());
        subcommands.add(new RestoreGame());
        subcommands.add(new CardsInfoForPlayer());
        subcommands.add(new UpdateThreadArchiveTime());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
