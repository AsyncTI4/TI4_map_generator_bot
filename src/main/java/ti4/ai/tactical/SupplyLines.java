package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class SupplyLines {

    public static final String CUT_SUPPLY_LINES = "csl";
    private static final double OWNER_CAN_RETURN_HOLD = 0.5;

    static boolean chases(Game game, Player seat, Tile tile, ObjectiveValue objectives) {
        return seat.getSecretsUnscored().containsKey(CUT_SUPPLY_LINES)
                && objectives.progress(CUT_SUPPLY_LINES, objectives.before())
                        < ListPlayerInfoService.getObjectiveThreshold(CUT_SUPPLY_LINES, game)
                && !dockOwners(game, seat, tile).isEmpty();
    }

    static double survival(Game game, Player seat, Tile tile, List<UnitMove> arriving) {
        int hitsToKillAll = hitsToKillAll(seat, arriving);
        if (hitsToKillAll == 0) return 0;
        List<Double> chances = CannonExposure.dieChances(game, seat, tile);
        return chances.isEmpty() ? 1 : 1 - DiceOdds.atLeast(chances, hitsToKillAll);
    }

    static double hold(Game game, Player seat, Tile tile) {
        double hold = 1;
        for (Player owner : dockOwners(game, seat, tile)) {
            if (!owner.isPassed() && shipsWithin(game, owner, tile)) hold = Math.min(hold, OWNER_CAN_RETURN_HOLD);
        }
        return hold;
    }

    public static boolean targetInReach(Game game, Player seat) {
        for (Tile tile : game.getTileMap().values()) {
            if (dockOwners(game, seat, tile).isEmpty() || BoardView.hasEnemyShips(game, seat, tile)) continue;
            if (shipsWithin(game, seat, tile)) return true;
        }
        return false;
    }

    public static List<Tile> dockTilesInReach(Game game, Player seat) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!dockOwners(game, seat, tile).isEmpty() && shipsWithin(game, seat, tile)) tiles.add(tile);
        }
        return tiles;
    }

    static List<Player> dockOwners(Game game, Player seat, Tile tile) {
        List<Player> owners = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            if (other != seat && hasDock(tile, other)) owners.add(other);
        }
        return owners;
    }

    private static boolean hasDock(Tile tile, Player owner) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (BoardView.count(holder, owner, UnitType.Spacedock) > 0) return true;
        }
        return false;
    }

    private static boolean shipsWithin(Game game, Player player, Tile target) {
        int widest = 0;
        for (Tile tile : game.getTileMap().values()) widest = Math.max(widest, longestMove(player, tile));
        if (widest == 0) return false;
        Map<String, Integer> reach = MovementGraph.reach(game, player, target.getPosition(), widest);
        for (Tile tile : game.getTileMap().values()) {
            int move = longestMove(player, tile);
            Integer distance = reach.get(tile.getPosition());
            if (move > 0 && distance != null && distance <= move) return true;
        }
        return false;
    }

    private static int longestMove(Player player, Tile tile) {
        int longest = 0;
        for (UnitType type : BoardView.ships(BoardView.space(tile), player).keySet()) {
            longest = Math.max(longest, BoardView.moveValueWithBoosts(player, type));
        }
        return longest;
    }

    private static int hitsToKillAll(Player seat, List<UnitMove> arriving) {
        int hits = 0;
        for (UnitMove move : arriving) {
            if (!BoardView.MOVING_SHIPS.contains(move.type()) && move.type() != UnitType.Fighter) continue;
            hits += move.count();
            if (move.type() != UnitType.Fighter && sustains(seat, move.type())) hits += move.count();
        }
        return hits;
    }

    private static boolean sustains(Player player, UnitType type) {
        return BoardView.model(player, type).filter(UnitModel::getSustainDamage).isPresent();
    }
}
