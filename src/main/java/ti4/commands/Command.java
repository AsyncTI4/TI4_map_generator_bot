package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface Command {

    String getActionID();

    default List<String> limitChannelsKeywords() {
        return new ArrayList<String>();
    }

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
            activeMap =  "Game: " + userActiveMap.getName();
        }
        String commandExecuted = "User: " + userName + ". " +activeMap+ " " +
                event.getName() + " " + event.getOptions().stream()
                .map(OptionMapping::getAsString)
                .collect(Collectors.joining(" "));

        MessageHelper.sendMessageToChannel(event.getChannel(), commandExecuted);
    }

    default boolean inAcceptableChannel(MessageChannel channel) {
        boolean acceptableChannel = false;
        List<String> limitedChannels = limitChannelsKeywords();
        for (int i = 0; i < limitedChannels.size(); i++) {
            acceptableChannel = acceptableChannel || channel.getName().contains(limitedChannels.get(i));
        }
        return limitedChannels.isEmpty() || acceptableChannel;
    }
}
