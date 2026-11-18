package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class DeepBreakthroughHandler {
    private static final String EVENT_HORIZON = "deepbt";
    private static final String EVENT_HORIZON_ALLOWANCE = "deepEventHorizon_";
    private static final String USE_EVENT_HORIZON = "useDeepEventHorizon";

    public static void offerEventHorizon(Game game, Player player) {
        if (!player.hasReadyBreakthrough(EVENT_HORIZON)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Event Horizon_ to move up to 2 ships from systems containing no planets and your command tokens.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + USE_EVENT_HORIZON, "Use Event Horizon"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(USE_EVENT_HORIZON)
    public static void useEventHorizon(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasReadyBreakthrough(EVENT_HORIZON)) {
            return;
        }
        BreakthroughCommandHelper.exhaustBreakthrough(player, EVENT_HORIZON);
        game.setStoredValue(EVENT_HORIZON_ALLOWANCE + player.getFaction(), "active");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may need to press \"Reset all unit movement\" for the systems to appear."
                        + "\n-# You may move up to 2 ships using _Event Horizon_. They may come from the same system and may transport units.");
    }

    public static boolean hasEventHorizonAllowance(Game game, Player player) {
        return "active".equals(game.getStoredValue(EVENT_HORIZON_ALLOWANCE + player.getFaction()));
    }

    public static void clearEventHorizon(Game game, Player player) {
        game.removeStoredValue(EVENT_HORIZON_ALLOWANCE + player.getFaction());
    }
}
