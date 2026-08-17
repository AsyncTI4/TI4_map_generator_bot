package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.message.MessageHelper;

@UtilityClass
public class CombatInitiativeLLButtonHandler {
    private static final String RESOLVE = "resolveCombatInitiative";
    public static final String STATE = "combatInitiative_";

    @ButtonHandler(RESOLVE)
    public static void resolveCombatInitiative(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        game.setStoredValue(STATE + player.getFaction(), "yes");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " is performing a tactical action with _Combat Initiative_. A command token will be placed from"
                        + " reinforcements without spending one from the tactic pool.\n-# Move only 1 non-fighter ship during this tactical action.");
        ButtonHelperTacticalAction.beginTacticalAction(game, player);
        ButtonHelper.deleteMessage(event);
    }
}
