package ti4.ai.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

public record CannonDice(int dice, int hitsOn) {

    private static final String XXCHA_MECH = "xxcha_mech";
    private static final String ARTICLES_OF_WAR = "articles_war";
    private static final String ANTIMASS_DEFLECTORS = "amd";
    private static final String ABSOL_ANTIMASS_DEFLECTORS = "absol_amd";

    public static List<CannonDice> of(Game game, Tile tile, Player shooter, Collection<UnitHolder> leaving) {
        List<CannonDice> stacks = new ArrayList<>();
        for (String position : Adjacency.withSystem(game, shooter, tile.getPosition())) {
            Tile nearby = game.getTileByPosition(position);
            if (nearby == null) continue;
            boolean inSystem = nearby == tile;
            for (UnitHolder holder : nearby.getUnitHolders().values()) {
                if (leaving.contains(holder)) continue;
                for (UnitKey key : holder.getUnitKeysForPlayer(shooter)) {
                    UnitModel model = shooter.getUnitFromUnitKey(key);
                    if (model == null || (!inSystem && !model.getDeepSpaceCannon(shooter))) continue;
                    if (XXCHA_MECH.equals(model.getId()) && ButtonHelper.isLawInPlay(game, ARTICLES_OF_WAR)) continue;
                    int dice = model.getSpaceCannonDieCount(shooter) * holder.getUnitCount(key);
                    stacks.add(new CannonDice(dice, model.getSpaceCannonHitsOn(shooter)));
                }
            }
        }
        return stacks;
    }

    public static List<Double> againstShipsOf(Game game, Player target, Tile tile) {
        List<Double> chances = new ArrayList<>();
        if (tile.isScar(game)) return chances;
        for (Player shooter : game.getRealPlayers()) {
            if (shooter != target) chances.addAll(fromShooter(game, tile, shooter, target));
        }
        return chances;
    }

    public static List<Double> fromShooter(Game game, Tile tile, Player shooter, Player target) {
        List<Double> chances = new ArrayList<>();
        if (tile.isScar(game)) return chances;
        int penalty = target.hasTech(ANTIMASS_DEFLECTORS) || target.hasTech(ABSOL_ANTIMASS_DEFLECTORS) ? 1 : 0;
        for (CannonDice stack : of(game, tile, shooter, List.of())) {
            double chance = hitChance(stack.hitsOn() + penalty);
            for (int die = 0; die < stack.dice(); die++) chances.add(chance);
        }
        return chances;
    }

    public static int landingHitPoints(
            Player owner, Map<UnitType, Integer> ships, Map<UnitType, Integer> damaged, int groundForces) {
        int hitPoints = 0;
        int capacity = 0;
        for (Map.Entry<UnitType, Integer> ship : ships.entrySet()) {
            UnitType type = ship.getKey();
            if (!BoardView.MOVING_SHIPS.contains(type)) continue;
            int count = ship.getValue();
            hitPoints += count;
            capacity += BoardView.capacity(owner, type) * count;
            boolean sustains = BoardView.model(owner, type)
                    .filter(UnitModel::getSustainDamage)
                    .isPresent();
            if (sustains) hitPoints += Math.max(0, count - damaged.getOrDefault(type, 0));
        }
        int carriedFighters =
                Math.min(ships.getOrDefault(UnitType.Fighter, 0), Math.max(0, capacity - Math.max(1, groundForces)));
        return Math.max(1, hitPoints + carriedFighters);
    }

    public static double hitChance(int hitsOn) {
        return Math.max(0, 11 - hitsOn) / 10.0;
    }
}
