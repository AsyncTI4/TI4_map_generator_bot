package ti4.commands.bothelper;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class BothelperCommand implements Command {

    private final Collection<BothelperSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.BOTHELPER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            Member member = event.getMember();
            if (member != null) {
                java.util.List<Role> roles = member.getRoles();
                if (roles.contains(MapGenerator.adminRole) || roles.contains(MapGenerator.developerRole) || roles.contains(MapGenerator.bothelperRole)) {
                    return true;
                } else {
                    MessageHelper.replyToMessage(event, "Not Authorized command attempt");
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        Map userActiveMap = MapManager.getInstance().getUserActiveMap(user.getId());
        String activeMap = "";
        if (userActiveMap != null) {
            activeMap = "Active map: " + userActiveMap.getName();
        }
        String commandExecuted = "User: " + userName + " executed command. " + activeMap + "\n" +
                event.getName() + " " + event.getInteraction().getSubcommandName() + " " + event.getOptions().stream()
                .map(option -> option.getName() + ":" + getOptionValue(option))
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    private String getOptionValue(OptionMapping option) {
        if (option.getName().equals(Constants.PLAYER)) {
            return option.getAsUser().getName();
        }
        return option.getAsString();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        BothelperSubcommandData subCommandExecuted = null;
        String subcommandName = event.getInteraction().getSubcommandName();
        for (BothelperSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subCommandExecuted = subcommand;
                break;
            }
        }
    }

    protected String getActionDescription() {
        return "Bothelper";
    }

    private Collection<BothelperSubcommandData> getSubcommands() {
        Collection<BothelperSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new ImportTTPG());
        subcommands.add(new CreateGameChannels());
        subcommands.add(new ServerLimitStats());
        subcommands.add(new ListOldChannels());
        subcommands.add(new ArchiveOldThreads());
        subcommands.add(new FixGameChannelPermissions());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
