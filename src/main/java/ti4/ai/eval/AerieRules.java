package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.TickMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class AerieRules {

    private static final String AERIE_HOLOLATTICE = "ah";
    private static final String MEMO = "aerie.blocks";

    public static boolean blocks(Game game, Player player, Tile tile) {
        Map<String, Boolean> memo = TickMemo.mapFor(game, MEMO);
        return memo.computeIfAbsent(
                player.getFaction() + "#" + tile.getPosition(), key -> anyLatticeHolderBuiltIn(game, player, tile));
    }

    private static boolean anyLatticeHolderBuiltIn(Game game, Player player, Tile tile) {
        return game.getRealPlayersNDummies().stream()
                .filter(other -> other != player && !player.getAllianceMembers().contains(other.getFaction()))
                .filter(other -> other.hasTech(AERIE_HOLOLATTICE))
                .anyMatch(other -> hasStructure(other, tile));
    }

    private static boolean hasStructure(Player owner, Tile tile) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (holder.getUnitCount(UnitType.Spacedock, owner.getColor()) > 0
                    || holder.getUnitCount(UnitType.Pds, owner.getColor()) > 0) {
                return true;
            }
        }
        return false;
    }
}
