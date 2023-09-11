package ti4.commands.admin;

import java.util.List;
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
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

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
                for (Role role : MapGenerator.adminRoles) {
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
    public void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        Game userActiveGame = GameManager.getInstance().getUserActiveGame(user.getId());
        String activeGame = "";
        if (userActiveGame != null) {
            activeGame = "Active map: " + userActiveGame.getName();
        }
        String commandExecuted = "User: " + userName + " executed command. " + activeGame + "\n" +
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
        subcommands.add(new SaveMaps());
        subcommands.add(new SaveMap());
        subcommands.add(new ResetEmojiCache());
        subcommands.add(new ReloadMap());
        subcommands.add(new FixTheFrankens());
        subcommands.add(new CardsInfoForPlayer());
        subcommands.add(new DrawSpecificSOForPlayer());
        subcommands.add(new Statistics());
        subcommands.add(new SetGlobalSetting());
        subcommands.add(new UpdateThreadArchiveTime());
        subcommands.add(new RunManualDataMigration());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addSubcommands(getSubcommands()));
    }
}
