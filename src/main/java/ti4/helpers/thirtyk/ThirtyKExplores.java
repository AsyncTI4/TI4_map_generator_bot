package ti4.helpers.thirtyk;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ThirtyKExplores {

    public static void resolveExplore(
            GenericInteractionCreateEvent event, String cardID, Tile tile, String planetID, Player player, Game game) {
        if (!ThirtyKCards.isExplore(cardID)) return;
    }
}
