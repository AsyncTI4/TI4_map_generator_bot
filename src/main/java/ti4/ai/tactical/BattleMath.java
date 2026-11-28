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
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CannonDice;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class BattleMath {

    private static final String ASSAULT_CANNON = "asc";
    private static final int ASSAULT_CANNON_SHIPS = 3;

    static int expectedCannonHits(Game game, Player seat, Tile tile) {
        return (int) Math.round(expectedCannonFire(game, seat, tile));
    }

    static double expectedCannonFire(Game game, Player seat, Tile tile) {
        return CannonExposure.expectedHits(game, seat, tile);
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

    static List<CannonDice> cannonDice(Game game, Tile tile, Player shooter, Collection<UnitHolder> leaving) {
        return CannonDice.of(game, tile, shooter, leaving);
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

    static List<Double> ownCannonDice(Game game, Player seat, Tile tile, Collection<Tile> origins) {
        List<UnitHolder> leaving = origins.stream().map(BoardView::space).toList();
        List<Double> chances = new ArrayList<>();
        for (CannonDice stack : cannonDice(game, tile, seat, leaving)) {
            for (int die = 0; die < stack.dice(); die++) chances.add(hitChance(stack.hitsOn()));
        }
        return chances;
    }

    static List<Double> spaceCannonDice(Player owner, UnitHolder holder) {
        List<Double> chances = new ArrayList<>();
        for (UnitKey key : holder.getUnitKeysForPlayer(owner)) {
            UnitModel model = owner.getUnitFromUnitKey(key);
            if (model == null) continue;
            int dice = model.getSpaceCannonDieCount(owner) * holder.getUnitCount(key);
            for (int die = 0; die < dice; die++) chances.add(hitChance(model.getSpaceCannonHitsOn(owner)));
        }
        return chances;
    }

    static List<Double> barrageDice(Player shooter, List<Combatant> ships) {
        List<Double> chances = new ArrayList<>();
        for (Combatant ship : ships) {
            UnitModel model = ship.type() == null ? null : shooter.getUnitByType(ship.type());
            if (model == null) continue;
            double chance = hitChance(model.getAfbHitsOn(shooter));
            for (int die = 0; die < model.getAfbDieCount(shooter); die++) chances.add(chance);
        }
        return chances;
    }

    static int fighters(List<Combatant> ships) {
        return (int)
                ships.stream().filter(ship -> ship.type() == UnitType.Fighter).count();
    }

    static List<Combatant> withoutFighters(List<Combatant> ships, int destroyed) {
        if (destroyed <= 0) return ships;
        List<Combatant> remaining = new ArrayList<>(ships.size());
        int left = destroyed;
        for (Combatant ship : ships) {
            if (left > 0 && ship.type() == UnitType.Fighter) {
                left--;
                continue;
            }
            remaining.add(ship);
        }
        return remaining;
    }

    static boolean firesAssaultCannon(Player shooter, List<Combatant> ships) {
        return shooter.hasTech(ASSAULT_CANNON) && nonFighterShips(ships) >= ASSAULT_CANNON_SHIPS;
    }

    static List<Combatant> afterAssaultCannon(List<Combatant> target) {
        Optional<Combatant> cheapest =
                target.stream().filter(BattleMath::isNonFighterShip).min(Combatant.LOSS_ORDER);
        if (cheapest.isEmpty()) return target;
        List<Combatant> remaining = new ArrayList<>(target);
        remaining.remove(cheapest.get());
        return remaining;
    }

    private static int nonFighterShips(List<Combatant> ships) {
        return (int) ships.stream().filter(BattleMath::isNonFighterShip).count();
    }

    private static boolean isNonFighterShip(Combatant ship) {
        return ship.type() != null && ship.type() != UnitType.Fighter;
    }

    static int hitPoints(List<Combatant> ships) {
        return ships.size() + (int) ships.stream().filter(Combatant::sustain).count();
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
        return CannonDice.hitChance(hitsOn);
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
        remaining.sort(Combatant.LOSS_ORDER);
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
