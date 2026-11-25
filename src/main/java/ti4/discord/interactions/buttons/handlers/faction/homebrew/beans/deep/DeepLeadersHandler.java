package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class DeepLeadersHandler {

    public static void resolveHero(GenericInteractionCreateEvent event, Game game, Player player) {
        DeepUnitHandler.transform(player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " transformed The Deep into The Deep, Ascended.");
    }
}
