package ti4.helpers;

import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.Nullable;
import ti4.commands.cards.CardsInfo;
import ti4.map.Map;
import ti4.map.MapManager;

import javax.annotation.CheckForNull;

public class FoWHelper {

    public static Boolean isPrivateGame(ButtonInteractionEvent event) {
        return isGamePrivate(null, null, event.getChannel());
    }

    public static Boolean isPrivateGame(GenericCommandInteractionEvent event) {
        return isPrivateGame(null, event);
    }

    public static Boolean isPrivateGame(@CheckForNull Map map, GenericCommandInteractionEvent event) {
        return isGamePrivate(map, event, null);
    }

    private static Boolean isGamePrivate(@Nullable Map map, @CheckForNull GenericCommandInteractionEvent event, @CheckForNull Channel channel_) {
        Boolean isFoWPrivate = null;
        if (event == null && channel_ == null) {
            return null;
        }
        if (map == null) {
            Channel channel = channel_ != null ? channel_ : event.getChannel();
            if (channel == null) {
                return isFoWPrivate;
            }
            String gameName = channel.getName();
            gameName = gameName.replace(CardsInfo.CARDS_INFO, "");
            gameName = gameName.substring(0, gameName.indexOf("-"));
            map = MapManager.getInstance().getMap(gameName);
        }
        if (map == null) {
            return isFoWPrivate;
        }
        if (map.isFoWMode() && channel_ != null || event != null) {
            Channel channel = channel_ != null ? channel_ : event.getChannel();
            if (channel == null) {
                return isFoWPrivate;
            }
            isFoWPrivate = channel.getName().endsWith(Constants.PRIVATE_CHANNLE);
        }
        return isFoWPrivate;
    }
}
