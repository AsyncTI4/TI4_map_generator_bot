package ti4.helpers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class DiploSystemHelper {

    public static boolean diploSystem(
            GenericInteractionCreateEvent event, Game game, Player player, String tileToResolve) {
        String tileID = AliasHandler.resolveTile(tileToResolve);

        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return false;
        }

        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player
                    && player_.isRealPlayer()
                    && !player.getAllianceMembers().contains(player_.getFaction())) {
                String color = player_.getColor();
                if (Mapper.isValidColor(color)) {
                    CommandCounterHelper.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, game, color);
                }
            }
        }

        return true;
    }
}
