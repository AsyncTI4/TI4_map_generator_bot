package ti4.commands;

import java.util.Arrays;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.map.GameManager;

@UtilityClass
public class CommandHelper {

    public static List<Choice> toChoices(String... values) {
        return toChoices(Arrays.asList(values));
    }

    public static List<Choice> toChoices(List<String> values) {
        return values.stream().map(v -> new Choice(v, v)).toList();
    }

    @NotNull
    public static String getGameName(SlashCommandInteraction event) {
        // try to get game name from channel name
        var channel = event.getChannel();
        String gameName = getGameNameFromChannelName(channel.getName());
        if (GameManager.isValidGame(gameName)) {
            return gameName;
        }
        // if a thread, try to get game name from parent
        if (channel instanceof ThreadChannel) {
            IThreadContainerUnion parentChannel = ((ThreadChannel) channel).getParentChannel();
            gameName = getGameNameFromChannelName(parentChannel.getName());
        }
        if (GameManager.isValidGame(gameName)) {
            return gameName;
        }
        return gameName;
    }

    private static String getGameNameFromChannelName(String channelName) {
        String gameName = channelName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
        gameName = gameName.replace(Constants.BAG_INFO_THREAD_PREFIX, "");
        gameName = StringUtils.substringBefore(gameName, "-");
        return gameName;
    }
}
