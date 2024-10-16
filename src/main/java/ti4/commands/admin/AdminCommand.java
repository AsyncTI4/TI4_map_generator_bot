package ti4.commands.admin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class AdminCommand implements Command {

    private final Collection<AdminSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.ADMIN;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            Member member = event.getMember();
            if (member != null) {
                List<Role> roles = member.getRoles();
                for (Role role : AsyncTI4DiscordBot.adminRoles) {
                    if (roles.contains(role)) {
                        return true;
                    }
                }
                MessageHelper.replyToMessage(event, "You are not authorized to use this command. You must have the @Admin role.");
                return false;
            }
        }
        return false;
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
        subcommands.add(new SaveMaps());
        subcommands.add(new SaveMap());
        subcommands.add(new ResetEmojiCache());
        subcommands.add(new ResetImageCache());
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
