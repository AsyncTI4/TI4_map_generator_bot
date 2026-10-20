package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class CannonWipeRules {

    public static final String TURN_THEIR_FLEETS_TO_DUST = "ttfd";
    private static final String ANTIMASS_DEFLECTORS = "amd";
    private static final String ABSOL_ANTIMASS_DEFLECTORS = "absol_amd";
    private static final String ARGENT_FLAGSHIP = "argent_flagship";

    public static boolean held(Player seat) {
        return seat.getSecretsUnscored().containsKey(TURN_THEIR_FLEETS_TO_DUST);
    }

    static double chanceToWipe(Game game, Player seat, Tile tile, Player opponent, Map<UnitType, Integer> arriving) {
        if (!held(seat)
                || !opponent.isRealPlayer()
                || ButtonHelper.doesPlayerHaveFSHere(ARGENT_FLAGSHIP, opponent, tile)) {
            return 0;
        }
        int hitsNeeded = hitsToWipe(BoardView.space(tile), opponent);
        if (hitsNeeded == 0) return 0;
        List<Double> chances = dieChances(game, seat, tile, opponent, arriving);
        return chances.isEmpty() ? 0 : DiceOdds.atLeast(chances, hitsNeeded);
    }

    public static boolean coversALoneShip(Game game, Player seat, Tile tile) {
        Player opponent = TacticalPlanner.singleOpponent(game, seat, tile);
        return held(seat)
                && opponent != null
                && opponent.isRealPlayer()
                && BoardView.nonFighterShips(BoardView.space(tile), opponent) == 1
                && ButtonHelper.getPlayersWithPds2Cover(seat, game, tile.getPosition())
                        .contains(seat);
    }

    private static int hitsToWipe(UnitHolder space, Player opponent) {
        int needed = 0;
        int nonFighters = 0;
        for (Map.Entry<UnitType, Integer> ship :
                BoardView.ships(space, opponent).entrySet()) {
            UnitType type = ship.getKey();
            needed += ship.getValue();
            if (type == UnitType.Fighter) continue;
            nonFighters += ship.getValue();
            if (sustains(opponent, type)) needed += BoardView.undamaged(space, opponent, type);
        }
        return nonFighters == 0 ? 0 : needed;
    }

    private static boolean sustains(Player player, UnitType type) {
        return BoardView.model(player, type).filter(UnitModel::getSustainDamage).isPresent();
    }

    private static List<Double> dieChances(
            Game game, Player seat, Tile tile, Player opponent, Map<UnitType, Integer> arriving) {
        int penalty = opponent.hasTech(ANTIMASS_DEFLECTORS) || opponent.hasTech(ABSOL_ANTIMASS_DEFLECTORS) ? 1 : 0;
        List<Double> chances = new ArrayList<>();
        boolean covered = ButtonHelper.getPlayersWithPds2Cover(seat, game, tile.getPosition())
                .contains(seat);
        if (covered) {
            for (BattleMath.CannonDice stack : BattleMath.cannonDice(game, tile, seat, List.of())) {
                addDice(chances, stack.dice(), stack.hitsOn() + penalty);
            }
        }
        if (!tile.isScar(game)) {
            arriving.forEach((type, count) -> {
                UnitModel model = seat.getUnitByType(type);
                if (model == null || count <= 0) return;
                addDice(
                        chances,
                        model.getSpaceCannonDieCount(seat) * count,
                        model.getSpaceCannonHitsOn(seat) + penalty);
            });
        }
        return chances;
    }

    private static void addDice(List<Double> chances, int dice, int hitsOn) {
        double chance = BattleMath.hitChance(hitsOn);
        for (int die = 0; die < dice; die++) chances.add(chance);
    }
}
