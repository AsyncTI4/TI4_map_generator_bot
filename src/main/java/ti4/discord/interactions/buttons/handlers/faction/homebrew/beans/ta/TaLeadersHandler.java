package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import lombok.experimental.UtilityClass;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class TaLeadersHandler {

    public static void resolveTaCommander(Player player, Tile tile, String planetName) {
        if (tile == null || player == null) {
            return;
        }

        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        player.gainTG(1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Gained 1 "
                        + MiscEmojis.tg
                        + " from exploring "
                        + Helper.getPlanetRepresentationPlusEmoji(planetName)
                        + " due to _Zul_, the Ta Commander.");
        if (planet.hasAttachment() || TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)) {
            player.gainTG(1);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Gained 1 additional " + MiscEmojis.tg + " due to the planet having an attachment.");
        }
    }
}
