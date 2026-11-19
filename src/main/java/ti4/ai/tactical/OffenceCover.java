package ti4.ai.tactical;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameSnapshots;
import ti4.helpers.ButtonHelper;
import ti4.model.TileHelper;
import ti4.model.TileModel;

@UtilityClass
class OffenceCover {

    private static final Map<String, String> LOCKED_SIDE_OF = Map.of("82b", "82a", "82bh", "82ah");

    static List<Player> of(Game game, Player active, String position) {
        Tile tile = game.getTileByPosition(position);
        String lockedSide = tile == null ? null : LOCKED_SIDE_OF.get(tile.getTileID());
        if (lockedSide == null || !activatedWhileLocked(game, active, position, lockedSide)) {
            return ButtonHelper.getPlayersWithPds2Cover(active, game, position);
        }
        try {
            return coverOfTheLockedSide(game, active, position, lockedSide);
        } catch (RuntimeException e) {
            return ButtonHelper.getPlayersWithPds2Cover(active, game, position);
        }
    }

    private static boolean activatedWhileLocked(Game game, Player active, String position, String lockedSide) {
        if (!position.equals(game.getActiveSystem())) return false;
        TileModel locked = TileHelper.getTileById(lockedSide);
        if (locked == null) return false;
        String summary = game.getStoredValue("currentActionSummary" + active.getFaction());
        String activation = StringUtils.substringAfterLast(summary, " Activated ");
        return activation.startsWith(position + " (" + locked.getName() + ")");
    }

    private static List<Player> coverOfTheLockedSide(Game game, Player active, String position, String lockedSide) {
        Game copy = GameSnapshots.copy(game);
        Tile flipped = copy.getTileByPosition(position);
        copy.setTile(new Tile(lockedSide, position, flipped.getSpaceUnitHolder()));
        Player activeInCopy = copy.getPlayerFromColorOrFaction(active.getFaction());
        if (activeInCopy == null) return ButtonHelper.getPlayersWithPds2Cover(active, game, position);
        Set<String> factions = ButtonHelper.getPlayersWithPds2Cover(activeInCopy, copy, position).stream()
                .map(Player::getFaction)
                .collect(Collectors.toSet());
        return game.getRealPlayers().stream()
                .filter(player -> factions.contains(player.getFaction()))
                .toList();
    }
}
