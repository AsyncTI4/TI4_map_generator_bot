package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class OnyxxaCommanderButtonHandler {

    // Commander: Strategist Kreel — unlock condition:
    // "Resolve the primary ability of a strategy card in 1 of your neighbors' play areas."
    public static void offerCommanderUnlockButton(Player player) {
        Button button = Buttons.gray(
                player.factionButtonChecker() + "onyxxaCommanderUnlock",
                "Unlock Strategist Kreel",
                FactionEmojis.onyxxa);
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", only click if you resolved the primary of a strategy card in a neighbor's play area.",
                button);
    }

    @ButtonHandler("onyxxaCommanderUnlock")
    public static void handleCommanderUnlock(ButtonInteractionEvent event, Game game, Player player) {
        UnlockLeaderService.unlockLeader("onyxxacommander", game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // TODO: implement commander ability (Strategist Kreel):
    // After you draw a relic: gain 1 command token.
    // After you gain control of a planet in The Fracture that was controlled by another player: gain 1 relic.
}
