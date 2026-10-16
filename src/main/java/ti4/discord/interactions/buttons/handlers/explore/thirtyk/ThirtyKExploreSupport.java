package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

// TODO: 30k explores and relics that place units do not check reinforcement limits.
@UtilityClass
public class ThirtyKExploreSupport {

    public static Button decline(Player player) {
        return Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline");
    }

    public static List<Tile> tilesInOrAdjacent(Game game, Player player, Tile tile) {
        List<Tile> tiles = new ArrayList<>();
        if (tile == null) return tiles;
        Set<String> positions = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true);
        for (String position : positions) {
            Tile adjacent = game.getTileByPosition(position);
            if (adjacent != null) tiles.add(adjacent);
        }
        return tiles;
    }

    public static boolean isShip(Player player, UnitKey key) {
        UnitModel model = player.getUnitFromUnitKey(key);
        return model != null && model.getIsShip();
    }

    public static int hitCapacityOfShips(Player player, Tile tile) {
        UnitHolder space = tile.getSpaceUnitHolder();
        int capacity = 0;
        for (UnitKey key : space.getUnitKeysForPlayer(player)) {
            UnitModel model = player.getUnitFromUnitKey(key);
            if (model == null || !model.getIsShip()) continue;
            int count = space.getUnitCount(key);
            capacity += count;
            if (model.getSustainDamage()) {
                capacity += count - space.getDamagedUnitCount(key);
            }
        }
        return capacity;
    }

    public static boolean otherPlayersHaveShips(Game game, Player player, Tile tile) {
        return tile != null && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game);
    }

    public static boolean hasUnitOfType(Player player, UnitHolder holder, UnitType type) {
        return holder != null && holder.getUnitCount(type, player) > 0;
    }
}
