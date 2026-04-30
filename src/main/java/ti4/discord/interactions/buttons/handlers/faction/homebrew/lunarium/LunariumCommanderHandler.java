package ti4.discord.interactions.buttons.handlers.faction.homebrew.lunarium;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.objectives.DrawSecretService;

@UtilityClass
public class LunariumCommanderHandler {

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " draws 1 secret objective due to " + FactionEmojis.lunarium
                        + " **Lunarium Commander**.");
        DrawSecretService.drawSO(event, game, player);
    }
}
