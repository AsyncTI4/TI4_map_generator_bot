package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CommandCounterHelper;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;

@UtilityClass
public class KaloraBreakthroughHandler {

    public static void bypassOperationsRetreat(
            Player player, Game game, Tile activeTile, GenericInteractionCreateEvent event) {
        if (!CommandCounterHelper.hasCC(player, activeTile)) return;
        RemoveCommandCounterService.fromTile(player.getColor(), activeTile, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji()
                        + " returned 1 command token from the active system to their reinforcements via **Bypass Operations**.");
    }
}
