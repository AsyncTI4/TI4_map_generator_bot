package ti4.service.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.map.persistence.GameManager;

@UtilityClass
public class GameNameService {

    @NotNull
    public static String getGameName(SlashCommandInteractionEvent event) {
        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        if (gameNameOption != null) {
            return gameNameOption.getAsString();
        }
        return getGameNameFromChannel(event);
    }

    public static String getGameNameFromChannel(Interaction event) {
        return getGameNameFromChannel(event.getChannel());
    }

    @NotNull
    public static String getGameNameFromChannel(Channel channel) {
        String gameName = getGameNameFromChannelName(channel.getName());
        if (GameManager.isValid(gameName)) {
            return gameName;
        }
        if (channel instanceof ThreadChannel) {
            IThreadContainerUnion parentChannel = ((ThreadChannel) channel).getParentChannel();
            gameName = getGameNameFromChannelName(parentChannel.getName());
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
