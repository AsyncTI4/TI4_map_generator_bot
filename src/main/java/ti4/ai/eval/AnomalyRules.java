package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.TickMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class AnomalyRules {

    private static final String VOIDBORN = "voidborn";
    private static final String CIRCLET_OF_THE_VOID = "circletofthevoid";
    private static final String SHARED_RESEARCH = "shared_research";
    private static final String DIMENSIONAL_TEAR = "cabal_spacedock";
    private static final String MAGMUS_REACTOR = "mr";
    private static final String GASHLAI_PHYSIOLOGY = "gashlai_physiology";
    private static final String NEBULA_MEMO = "anomaly.nebula";

    public static boolean ignoresNebula(Player player) {
        return player.hasAbility(VOIDBORN) || holdsCirclet(player);
    }

    public static boolean retreatsIntoNebulae(Player player) {
        return player.hasAbility(VOIDBORN);
    }

    public static boolean holdsCirclet(Player player) {
        return player.getRelics().contains(CIRCLET_OF_THE_VOID);
    }

    public static boolean passesNebulae(Game game, Player player) {
        return ignoresNebula(player) || ButtonHelper.isLawInPlay(game, SHARED_RESEARCH);
    }

    public static boolean passesSupernovas(Player player) {
        return holdsCirclet(player) || player.hasTech(MAGMUS_REACTOR) || player.hasAbility(GASHLAI_PHYSIOLOGY);
    }

    public static boolean isNebula(Game game, Tile tile) {
        Map<String, Boolean> memo = TickMemo.mapFor(game, NEBULA_MEMO);
        return memo.computeIfAbsent(tile.getPosition(), position -> tile.isNebula(game));
    }

    public static boolean startsCapped(Game game, Player player, Tile origin) {
        return isNebula(game, origin) && !ignoresNebula(player) && !ignoresAnomalies(game, player);
    }

    public static boolean ignoresAnomalies(Game game, Player seat) {
        return MoveEnablers.active(game, seat, MoveEnablers.Enabler.NAV_SUITE);
    }

    public static boolean riftBonus(Game game, Player seat, Tile tile) {
        return tile.isGravityRift(game, seat) && !ignoresAnomalies(game, seat);
    }

    public static boolean rollsLeaving(Game game, Player seat, Tile tile) {
        return riftBonus(game, seat, tile) && rollsIn(game, seat, tile);
    }

    public static boolean rollsOnRetreat(Game game, Player seat, Tile tile) {
        return tile.isGravityRift(game, seat) && rollsIn(game, seat, tile);
    }

    private static boolean rollsIn(Game game, Player seat, Tile tile) {
        return !holdsCirclet(seat) && !holdsDimensionalTear(game, seat, tile);
    }

    public static boolean holdsDimensionalTear(Game game, Player player, Tile tile) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                if (isDimensionalTear(player, key)) return true;
            }
        }
        return false;
    }

    public static boolean isDimensionalTear(Player owner, UnitKey key) {
        if (key.unitType() != UnitType.Spacedock) return false;
        UnitModel model = owner.getUnitFromUnitKey(key);
        return model != null && model.getId().contains(DIMENSIONAL_TEAR);
    }
}
