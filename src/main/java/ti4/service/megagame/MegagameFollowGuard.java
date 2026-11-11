package ti4.service.megagame;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;

@UtilityClass
public class MegagameFollowGuard {

    public static boolean refusesCard(ButtonInteractionEvent event, Game game, Player player, int sc) {
        if (!MegagameGroupService.isActive(game)
                || player == null
                || MegagameGroupService.sameGroup(game, player, sc)) {
            return false;
        }
        String group = MegagameGroupService.groupOf(game, player);
        String yourGroup = group == null ? "no strategy card group" : MegagameGroupService.label(group);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event,
                "This strategy card belongs to " + MegagameGroupService.label(MegagameGroupService.groupOfSc(sc))
                        + ". You are in " + yourGroup + ", so you cannot follow it.");
        return true;
    }

    public static boolean refusesPressOnOtherGroupsCard(ButtonInteractionEvent event, Game game, Player player) {
        if (!MegagameGroupService.isActive(game)) {
            return false;
        }
        int sc = cardOfPressedMessage(event, game);
        return game.getSCList().contains(sc) && refusesCard(event, game, player, sc);
    }

    private static int cardOfPressedMessage(ButtonInteractionEvent event, Game game) {
        if (event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD) {
            String threadName = event.getChannel().getName();
            String prefix = game.getName() + "-round-" + game.getRound() + "-";
            if (StringUtils.startsWithIgnoreCase(threadName, prefix)) {
                return MegagameScThreads.scOfThreadName(threadName.substring(prefix.length()));
            }
        }
        String roundPrefix = game.getRound() + "::";
        return GameMessageManager.getOne(game.getName(), event.getMessageId())
                .filter(message -> message.type() == GameMessageType.STRATEGY_CARD)
                .map(GameMessage::key)
                .filter(key -> key != null && key.startsWith(roundPrefix))
                .map(key -> NumberUtils.toInt(StringUtils.substringAfter(key, roundPrefix)))
                .orElse(0);
    }
}
