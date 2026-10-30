package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.Adjacency;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class BattleMath {

    private static final String XXCHA_MECH = "xxcha_mech";
    private static final String ARTICLES_OF_WAR = "articles_war";
    private static final String ASSAULT_CANNON = "asc";
    private static final int ASSAULT_CANNON_SHIPS = 3;

    static int expectedCannonHits(Game game, Player seat, Tile tile) {
        return (int) Math.round(expectedCannonFire(game, seat, tile));
    }

    static double expectedCannonFire(Game game, Player seat, Tile tile) {
        return CannonExposure.expectedHits(game, seat, tile);
    }

    static int ownCannonHits(Game game, Player seat, Tile tile, Collection<Tile> origins) {
        List<UnitHolder> leaving = origins.stream().map(BoardView::space).toList();
        return (int) Math.round(cannonFireLeaving(game, tile, seat, leaving));
    }

    static double cannonFire(Game game, Tile tile, Player shooter, @Nullable UnitHolder leaving) {
        return cannonFireLeaving(game, tile, shooter, leaving == null ? List.of() : List.of(leaving));
    }

    static double cannonFireLeaving(Game game, Tile tile, Player shooter, Collection<UnitHolder> leaving) {
        double expected = 0;
        for (CannonDice stack : cannonDice(game, tile, shooter, leaving)) {
            expected += stack.dice() * hitChance(stack.hitsOn());
        }
        return expected;
    }

    record CannonDice(int dice, int hitsOn) {}

    static List<CannonDice> cannonDice(Game game, Tile tile, Player shooter, Collection<UnitHolder> leaving) {
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

    static double spaceCannonHits(Player owner, UnitHolder holder) {
        double expected = 0;
        for (UnitKey key : holder.getUnitKeysForPlayer(owner)) {
            UnitModel model = owner.getUnitFromUnitKey(key);
            if (model == null) continue;
            int dice = model.getSpaceCannonDieCount(owner) * holder.getUnitCount(key);
            expected += dice * hitChance(model.getSpaceCannonHitsOn(owner));
        }
        return expected;
    }

    static Side afterBarrage(Side target, Side shooter) {
        int fighters = target.units().getOrDefault(UnitType.Fighter, 0);
        if (fighters == 0) return target;
        Player player = shooter.player();
        double expected = 0;
        for (Map.Entry<UnitType, Integer> entry : shooter.units().entrySet()) {
            UnitModel model = player.getUnitByType(entry.getKey());
            if (model == null) continue;
            expected += model.getAfbDieCount(player) * entry.getValue() * hitChance(model.getAfbHitsOn(player));
        }
        int destroyed = Math.min(fighters, (int) Math.round(expected));
        if (destroyed == 0) return target;
        Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);
        units.putAll(target.units());
        units.put(UnitType.Fighter, fighters - destroyed);
        return new Side(target.player(), units);
    }

    static Side afterAssaultCannon(Side target, Side shooter) {
        if (!firesAssaultCannon(shooter)) return target;
        Player player = target.player();
        Optional<UnitType> cheapest = target.units().entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter && entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .min(Comparator.comparingDouble(type ->
                        BoardView.model(player, type).map(UnitModel::getCost).orElse(0f)));
        if (cheapest.isEmpty()) return target;
        Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);
        units.putAll(target.units());
        units.merge(cheapest.get(), -1, Integer::sum);
        return new Side(player, units);
    }

    static boolean firesAssaultCannon(Side side) {
        int nonFighterShips = side.units().entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(Map.Entry::getValue)
                .sum();
        return side.player().hasTech(ASSAULT_CANNON) && nonFighterShips >= ASSAULT_CANNON_SHIPS;
    }

    static double hitChance(int hitsOn) {
        return Math.max(0, 11 - hitsOn) / 10.0;
    }

    static Map<UnitType, Integer> damagedAmong(UnitHolder holder, Player player, Map<UnitType, Integer> units) {
        Map<UnitType, Integer> damaged = new EnumMap<>(UnitType.class);
        units.forEach((type, count) -> {
            int hurt = Math.min(count, BoardView.damaged(holder, player, type));
            if (hurt > 0) damaged.put(type, hurt);
        });
        return damaged;
    }

    static List<Combatant> afterCannonFire(List<Combatant> ships, int hits) {
        List<Combatant> remaining = new ArrayList<>(ships);
        remaining.sort(Comparator.comparingDouble(Combatant::cost));
        for (int hit = 0; hit < hits && !remaining.isEmpty(); hit++) {
            Optional<Combatant> sustaining =
                    remaining.stream().filter(Combatant::sustain).findFirst();
            if (sustaining.isPresent()) {
                Combatant ship = sustaining.get();
                remaining.set(remaining.indexOf(ship), ship.withoutSustain());
            } else {
                remaining.removeFirst();
            }
        }
        return remaining;
    }
}
