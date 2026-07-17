package ti4.discord.interactions.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.logging.BotLogger;

@UtilityClass
class FollowStrategyCardButtonHandler {

    @ButtonHandler("requestAllFollow_")
    public static void requestAllFollow(ButtonInteractionEvent event, Game game) {
        game.setTemporaryPingDisable(true);
        event.getMessage()
                .reply(
                        game.getPing()
                                + ", someone has requested that everyone resolve this strategy card before play continues."
                                + " Please do so as soon as you can. The active player should not take an action until this is done.")
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
