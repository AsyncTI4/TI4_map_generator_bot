package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.stream.Collectors;

public interface Command {

    String getActionID();

    //If command can be executed for given command text
    boolean accept(SlashCommandInteractionEvent event);

    //Command action execution method
    void execute(SlashCommandInteractionEvent event);

    void registerCommands(CommandListUpdateAction commands);

    default void logBack(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String userName = user.getName();
        Map userActiveMap = MapManager.getInstance().getUserActiveMap(user.getId());
        String activeMap = "";
        if (userActiveMap != null){
            activeMap =  "Active map: " + userActiveMap.getName();
        }
        String commandExecuted = "User: " + userName + " executed command. " +activeMap+ "\n" +
                event.getName() + " " + event.getOptions().stream()
                .map(OptionMapping::getAsString)
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    default boolean isChannelCommand() {
        return true;
    }
}
