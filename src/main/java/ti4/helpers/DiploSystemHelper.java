package ti4.helpers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class DiploSystemHelper {

    public static boolean diploSystem(
            GenericInteractionCreateEvent event, Game game, Player player, String tileToResolve) {
        Tile tile = TileHelper.getTile(event, tileToResolve, game);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not resolve tileID:  `" + tileToResolve + "`. Tile not found");
            return false;
        }

        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player
                    && player_.isRealPlayer()
                    && !player.getAllianceMembers().contains(player_.getFaction())) {
                CommandCounterHelper.addCC(event, player_, tile);
                Helper.isCCCountCorrect(player_);
            }
        }

        return true;
    }
}
