package ti4.ai.eval;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.TickMemo;
import ti4.ai.winnu.LazaxGateFolding;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyIds;
import ti4.model.MapPositionMapper;

@UtilityClass
public class Adjacency {

    private static final String MOVEMENT_MEMO = "adjacency.movement";
    private static final String WITH_SYSTEM_MEMO = "adjacency.withSystem";
    private static final String ALPHA_BETA_MEMO = "adjacency.alphaBeta";
    private static final String WORMHOLE_MEMO = "adjacency.wormholes";
    private static final String VOID_TETHER_OWNER = "empyreanbt";

    public static Set<String> forMovement(Game game, Player player, String position) {
        return DecisionMemo.remember(
                () -> Collections.unmodifiableSet(movementLinks(game, player, position)),
                MOVEMENT_MEMO,
                player,
                position,
                MoveEnablers.key(game, player));
    }

    private static Set<String> movementLinks(Game game, Player player, String position) {
        Set<String> adjacent = new HashSet<>(FoWHelper.getAdjacentTiles(game, position, player, false, false, true));
        adjacent.addAll(LazaxGateFolding.movementLinks(game, player, position));
        if (MoveEnablers.active(game, player, MoveEnablers.Enabler.LOST_STAR_CHART)) {
            Set<String> charted = lostStarSystems(game, player);
            if (charted.contains(position)) adjacent.addAll(charted);
        }
        if (!player.hasUnlockedBreakthrough(VOID_TETHER_OWNER)) adjacent.removeAll(tetheredFrom(game, position));
        return adjacent;
    }

    public static Set<String> lostStarSystems(Game game, Player player) {
        Set<String> wormholes = alphaBetaSystems(game);
        @Nullable String mecatol = LazaxGateFolding.foldedMecatol(game, player);
        if (mecatol == null || wormholes.contains(mecatol)) return wormholes;
        Set<String> folded = new HashSet<>(wormholes);
        folded.add(mecatol);
        return Collections.unmodifiableSet(folded);
    }

    public static Set<String> alphaBetaSystems(Game game) {
        Map<String, Set<String>> memo = TickMemo.mapFor(game, ALPHA_BETA_MEMO);
        return memo.computeIfAbsent("all", ignored -> systemsWith(game, true));
    }

    public static Set<String> wormholeSystems(Game game) {
        Map<String, Set<String>> memo = TickMemo.mapFor(game, WORMHOLE_MEMO);
        return memo.computeIfAbsent("all", ignored -> systemsWith(game, false));
    }

    private static Set<String> systemsWith(Game game, boolean alphaOrBetaOnly) {
        Set<String> found = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getTileModel() == null || tile.getTileModel().isHyperlane()) continue;
            String position = tile.getPosition();
            boolean has = alphaOrBetaOnly
                    ? FoWHelper.doesTileHaveAlphaOrBeta(game, position)
                    : FoWHelper.doesTileHaveWHs(game, position);
            if (has) found.add(position);
        }
        return Collections.unmodifiableSet(found);
    }

    private static Set<String> tetheredFrom(Game game, String position) {
        Set<String> cut = new HashSet<>();
        for (BorderAnomalyHolder border : game.getBorderAnomalies()) {
            if (border == null || border.getTile() == null || !border.isType(BorderAnomalyIds.VOID_TETHER)) continue;
            @Nullable String across = across(game, border);
            if (across == null) continue;
            if (border.getTile().equals(position)) cut.add(across);
            if (across.equals(position)) cut.add(border.getTile());
        }
        return cut;
    }

    @Nullable
    private static String across(Game game, BorderAnomalyHolder border) {
        String override = game.getAdjacentTileOverride(border.getTile(), border.getDirection());
        if (override != null) return override;
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(border.getTile());
        if (neighbours == null || border.getDirection() < 0 || border.getDirection() >= neighbours.size()) {
            return null;
        }
        String position = neighbours.get(border.getDirection());
        return "x".equals(position) ? null : position;
    }

    public static Set<String> withSystem(Game game, Player player, String position) {
        return DecisionMemo.remember(
                () -> Collections.unmodifiableSet(FoWHelper.getAdjacentTiles(game, position, player, false, true)),
                WITH_SYSTEM_MEMO,
                player,
                position);
    }
}
