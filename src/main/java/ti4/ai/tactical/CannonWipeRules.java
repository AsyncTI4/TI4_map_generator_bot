package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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

    public static final boolean NEUTRAL_SHIPS_COUNT = true;

    public static boolean held(Player seat) {
        return seat.getSecretsUnscored().containsKey(TURN_THEIR_FLEETS_TO_DUST);
    }

    public static boolean mayBeTarget(Player player) {
        return player.isRealPlayer() || (NEUTRAL_SHIPS_COUNT && player.isNeutral());
    }

    public static List<Player> possibleTargets(Game game) {
        return game.getPlayers().values().stream()
                .filter(CannonWipeRules::mayBeTarget)
                .toList();
    }

    @Nullable
    public static Player shipOwner(Game game, Player seat, Tile tile) {
        List<Player> owners = possibleTargets(game).stream()
                .filter(other -> other != seat)
                .filter(other -> BoardView.nonFighterShips(BoardView.space(tile), other) > 0)
                .toList();
        return owners.size() == 1 ? owners.getFirst() : null;
    }

    static double chanceToWipe(Game game, Player seat, Tile tile, Player opponent, Map<UnitType, Integer> arriving) {
        if (!held(seat)
                || !mayBeTarget(opponent)
                || ButtonHelper.doesPlayerHaveFSHere(ARGENT_FLAGSHIP, opponent, tile)) {
            return 0;
        }
        int hitsNeeded = hitsToWipe(BoardView.space(tile), opponent);
        if (hitsNeeded == 0) return 0;
        List<Double> chances = dieChances(game, seat, tile, opponent, arriving);
        return chances.isEmpty() ? 0 : DiceOdds.atLeast(chances, hitsNeeded);
    }

    public static boolean coversAShip(Game game, Player seat, Tile tile) {
        Player opponent = shipOwner(game, seat, tile);
        return held(seat)
                && opponent != null
                && ButtonHelper.getPlayersWithPds2Cover(seat, game, tile.getPosition())
                        .contains(seat);
    }

    public static boolean hasCoveredTarget(Game game, Player seat) {
        return held(seat)
                && game.getTileMap().values().stream()
                        .anyMatch(tile -> shipOwner(game, seat, tile) != null && coversAShip(game, seat, tile));
    }

    public static double chanceToKillOneShip(Game game, Player seat, Tile tile) {
        if (tile.isScar(game)) return 0;
        List<Double> chances = new ArrayList<>();
        for (BattleMath.CannonDice stack : BattleMath.cannonDice(game, tile, seat, List.of())) {
            addDice(chances, stack.dice(), stack.hitsOn());
        }
        return chances.isEmpty() ? 0 : DiceOdds.atLeast(chances, 1);
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
