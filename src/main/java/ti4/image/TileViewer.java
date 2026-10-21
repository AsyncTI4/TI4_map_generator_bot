package ti4.image;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;

record TileViewer(boolean privateView, @Nullable Player player) {

    static TileViewer resolve(
            Game game, @Nullable GenericInteractionCreateEvent event, @Nullable Player explicitViewer) {
        boolean privateView = isPrivateView(game, event);
        return new TileViewer(privateView, explicitViewer != null ? explicitViewer : playerOf(game, event));
    }

    private static boolean isPrivateView(Game game, @Nullable GenericInteractionCreateEvent event) {
        return game.isFowMode()
                && event != null
                && (event.getMessageChannel().getName().endsWith(Constants.PRIVATE_CHANNEL)
                        || event instanceof UserOverridenGenericInteractionCreateEvent);
    }

    @Nullable
    private static Player playerOf(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (event == null) {
            return null;
        }
        return CommandHelper.getPlayerFromGame(
                game, event.getMember(), event.getUser().getId());
    }
}
