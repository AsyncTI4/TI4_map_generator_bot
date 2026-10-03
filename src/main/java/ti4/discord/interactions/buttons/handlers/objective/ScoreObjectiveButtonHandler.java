package ti4.discord.interactions.buttons.handlers.objective;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.interactions.listeners.context.ButtonContext;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.helpers.Constants;
import ti4.helpers.StatusHelper;

@UtilityClass
class ScoreObjectiveButtonHandler {

    @ButtonHandler(Constants.SO_SCORE_FROM_HAND)
    public static void scoreSecretFromHand(ButtonContext context) {
        MessageChannel mainGameChannel = context.getMainGameChannel();
        StatusHelper.soScoreFromHand(
                context.getEvent(),
                context.getButtonID(),
                context.getGame(),
                context.getPlayer(),
                context.getPrivateChannel(),
                mainGameChannel,
                mainGameChannel);
    }

    @ButtonHandler(Constants.PO_SCORING)
    public static void scorePublic(ButtonContext context) {
        StatusHelper.poScoring(
                context.getEvent(),
                context.getPlayer(),
                context.getButtonID(),
                context.getGame(),
                context.getPrivateChannel());
    }
}
