package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class FrameShiftBrActionCardHandler {
    private static final String RESOLVE = "resolveFrameShiftBr";
    private static final String STATE_PREFIX = "frameShiftBr_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Frame Shift"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveFrameShift(ButtonInteractionEvent event, Game game, Player player) {
        String activeSystem = game.getActiveSystem();
        if (activeSystem == null || activeSystem.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " cannot resolve _Frame Shift_ outside a tactical action.");
            return;
        }
        game.setStoredValue(getStateKey(player), activeSystem);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " resolved _Frame Shift_. Their ships that start movement in the fracture have +1 move, and wormhole systems are treated as ingress tokens for this tactical action.");
    }

    public static boolean isActive(Game game, Player player) {
        String activeSystem = game.getActiveSystem();
        return player != null && activeSystem != null && activeSystem.equals(game.getStoredValue(getStateKey(player)));
    }

    public static void clear(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(getStateKey(player));
        }
    }

    private static String getStateKey(Player player) {
        return STATE_PREFIX + player.getFaction();
    }
}
