package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.message.MessageHelper;

import java.util.stream.Collectors;

public interface Command {
    //If command can be executed for given command text
    boolean accept(SlashCommandInteractionEvent event);

    //Command action execution method
    void execute(SlashCommandInteractionEvent event);

    void registerCommands(CommandListUpdateAction commands);

    default void logBack(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        String userName = "N/A";
        if (member != null) {
            User user = member.getUser();
            if (user != null) {
                userName = user.getName();
            }
        }
        String commandExecuted = "User: " + userName + " executed command.\n" +
                event.getName() + " " + event.getOptions().stream()
                .map(OptionMapping::getAsString)
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    default boolean isChannelCommand() {
        return true;
    }
}
