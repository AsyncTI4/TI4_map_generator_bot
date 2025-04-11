package ti4.service.fow;

import org.apache.commons.lang3.StringUtils;

import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.service.option.FOWOptionService.FOWOption;

public class FOWPlusService {

    public static boolean isActive(Game game) {
        return game.getFowOption(FOWOption.FOW_PLUS);
    }

    public static boolean canActivatePosition(String position, Player player, Game game) {
        return !isActive(game) || FoWHelper.getTilePositionsToShow(game, player).contains(position);
    }

    public static boolean hideFogTile(String tileID, String label, Game game) {
        return isActive(game) && tileID.equals("0b") && StringUtils.isEmpty(label);
    }

    public static boolean tileAlwaysVisible(Tile tile, Player player, Game game) {
        return isActive(game) && !player.getFogLabels().keySet().contains(tile.getPosition()) && tile.isSupernova();
    }
  
}
