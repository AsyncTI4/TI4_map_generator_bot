package ti4.discord.interactions.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.service.megagame.MegagameGroupService;

@UtilityClass
class FollowStrategyCardButtonHandler {

    @ButtonHandler("requestAllFollow_")
    public static void requestAllFollow(ButtonInteractionEvent event, Game game, String buttonID) {
        game.setTemporaryPingDisable(true);
        int sc = NumberUtils.toInt(StringUtils.substringAfter(buttonID, "requestAllFollow_"));
        event.getMessage()
                .reply(
                        MegagameGroupService.pingForSc(game, sc)
                                + ", someone has requested that everyone resolve this strategy card before play continues."
                                + " Please do so as soon as you can. The active player should not take an action until this is done.")
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
