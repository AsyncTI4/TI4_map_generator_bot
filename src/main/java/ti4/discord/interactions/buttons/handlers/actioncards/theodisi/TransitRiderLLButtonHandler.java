package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.message.MessageHelper;

@UtilityClass
public class TransitRiderLLButtonHandler {
    private static final String RESOLVE = "resolveTransitRider";
    public static final String STATE = "transitRider_";

    public static void registerPrediction(Game game, Player player) {
        String abstainers = game.getStoredValue("Abstain On Agenda");
        if (!abstainers.contains(player.getFaction())) {
            game.setStoredValue("Abstain On Agenda", abstainers + player.getFaction());
        }
    }

    public static void offerReward(Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " correctly predicted the agenda outcome with _Transit Rider_. They may perform a tactical action in a system containing no other player's units without spending a command token.\n"
                        + "-# Unit abilities cannot be used during this tactical action; enforce that restriction manually.",
                List.of(Buttons.green(player.factionButtonChecker() + RESOLVE, "Use Transit Rider")));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveTransitRider(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        game.setStoredValue(STATE + player.getFaction(), "yes");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " is performing a tactical action with _Transit Rider_. A command token will be placed from reinforcements without spending one from the tactic pool.\n"
                        + "-# Choose a system containing no other player's units. Unit abilities cannot be used during this tactical action; enforce that restriction manually.");
        ButtonHelperTacticalAction.beginTacticalAction(game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isActive(Game game, Player player) {
        return !game.getStoredValue(STATE + player.getFaction()).isEmpty();
    }
}
