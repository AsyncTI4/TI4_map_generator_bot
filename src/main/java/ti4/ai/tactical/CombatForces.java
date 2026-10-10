package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.strategy.CopiedTechPolicy;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatStatsService;

@UtilityClass
class CombatForces {

    static final String DURANIUM_ARMOR = "da";
    static final String X89_DOUBLING = "x89c4";
    private static final String NON_EUCLIDEAN_SHIELDING = "nes";
    private static final String SINGULARITY = "technological_singularity";
    private static final int NEVER_HITS = 11;
    private static final double SWING_VALUE_PER_RESOURCE = 1.0;

    static List<Combatant> combatants(
            Game game, Tile tile, UnitHolder holder, Side side, Side opponent, Map<UnitType, Integer> damaged) {
        List<Combatant> combatants = new ArrayList<>();
        Map<UnitType, Integer> modifiers = CombatModifiers.hitModifiers(game, tile, holder, side, opponent);
        Player player = side.player();
        side.units().forEach((type, count) -> {
            UnitModel model = player.getUnitByType(type);
            if (model == null || count <= 0) return;
            CombatStatsService.CombatRoundProfile profile =
                    CombatStatsService.getCombatRoundProfile(true, model, player, tile, opponent.player(), false);
            int hitsOn = profile.hitsOn() - modifiers.getOrDefault(type, 0);
            int hurt = damaged.getOrDefault(type, 0);
            for (int i = 0; i < count; i++) {
                boolean sustain = model.getSustainDamage() && i >= hurt;
                combatants.add(new Combatant(hitsOn, profile.diceCount(), sustain, model.getCost(), type));
            }
        });
        if (player.hasTech(NON_EUCLIDEAN_SHIELDING)) combatants.addAll(secondCancelledHits(combatants));
        return combatants;
    }

    static Force force(Player player, List<Combatant> units, boolean ground) {
        Force force = Force.of(units);
        if (player.hasTech(DURANIUM_ARMOR)) force = force.repairing();
        if (ground && player.hasTech(X89_DOUBLING)) force = force.doublingHits();
        return force;
    }

    static Force withExpectedCopy(
            Game game, Player seat, Player opponent, Force own, Force enemy, boolean seatAttacks, boolean ground) {
        if (!seat.hasAbility(SINGULARITY)) return own;
        double baseWin = win(own, enemy, seatAttacks);
        double stake = stake(own, enemy);
        Force expected = own;
        double best = 0;
        for (String alias : copyable(game, seat, opponent)) {
            double value = CopiedTechPolicy.value(game, seat, alias);
            if (value <= 0) continue;
            Optional<Force> improved = improved(seat, own, alias, ground);
            Force candidate = improved.map(own::improvingAfterOpponentLoss).orElse(own);
            double swing = improved.isPresent() ? win(candidate, enemy, seatAttacks) - baseWin : 0;
            double score = value + swing * stake * SWING_VALUE_PER_RESOURCE;
            if (score > best) {
                best = score;
                expected = candidate;
            }
        }
        return expected;
    }

    static double copySwingValue(Game game, Player seat, Player opponent, String alias) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) return 0;
        boolean seatAttacks = game.getActivePlayer() == seat;
        UnitHolder space = BoardView.space(tile);
        if (BoardView.nonFighterShips(space, seat) + BoardView.count(space, seat, UnitType.Fighter) > 0
                && !BoardView.ships(space, opponent).isEmpty()) {
            return swingValue(game, tile, space, seat, opponent, alias, seatAttacks, false);
        }
        for (UnitHolder planet : BoardView.planets(tile)) {
            if (BoardView.groundForces(planet, seat) > 0 && BoardView.groundForces(planet, opponent) > 0) {
                return swingValue(game, tile, planet, seat, opponent, alias, seatAttacks, true);
            }
        }
        return 0;
    }

    private static double swingValue(
            Game game,
            Tile tile,
            UnitHolder holder,
            Player seat,
            Player opponent,
            String alias,
            boolean seatAttacks,
            boolean ground) {
        Force own = current(game, tile, holder, seat, opponent, ground);
        Force enemy = current(game, tile, holder, opponent, seat, ground);
        Optional<Force> improved = improved(seat, own, alias, ground);
        if (improved.isEmpty()) return 0;
        double swing = win(improved.get(), enemy, seatAttacks) - win(own, enemy, seatAttacks);
        return swing * stake(own, enemy) * SWING_VALUE_PER_RESOURCE;
    }

    static double spaceWinChance(Game game, Tile tile, Player seat, Player opponent) {
        UnitHolder space = BoardView.space(tile);
        Force own = current(game, tile, space, seat, opponent, false);
        Force enemy = current(game, tile, space, opponent, seat, false);
        return win(own, enemy, game.getActivePlayer() == seat);
    }

    static double garrisonHoldChance(Game game, Tile tile, UnitHolder planet, Player seat, Player invader) {
        Map<UnitType, Integer> invaders = unitsOf(BoardView.space(tile), invader, BoardView.GROUND_FORCES);
        if (invaders.isEmpty()) return 1.0;
        Side garrison = new Side(seat, unitsOf(planet, seat, BoardView.GROUND_FORCES));
        Side landing = new Side(invader, invaders);
        Force defence = force(seat, combatants(game, tile, planet, garrison, landing, damaged(planet, garrison)), true);
        Force attack = force(invader, combatants(game, tile, planet, landing, garrison, Map.of()), true);
        return CombatOdds.resolve(attack, defence).defenderWins();
    }

    private static Force current(
            Game game, Tile tile, UnitHolder holder, Player player, Player opponent, boolean ground) {
        Side side = new Side(
                player, ground ? unitsOf(holder, player, BoardView.GROUND_FORCES) : BoardView.ships(holder, player));
        Side other = new Side(
                opponent,
                ground ? unitsOf(holder, opponent, BoardView.GROUND_FORCES) : BoardView.ships(holder, opponent));
        return force(player, combatants(game, tile, holder, side, other, damaged(holder, side)), ground);
    }

    static Optional<Force> improved(Player seat, Force force, String alias, boolean ground) {
        if (DURANIUM_ARMOR.equals(alias)) return Optional.of(force.repairing());
        if (X89_DOUBLING.equals(alias)) return ground ? Optional.of(force.doublingHits()) : Optional.empty();
        boolean changed = false;
        List<Combatant> units = new ArrayList<>();
        for (Combatant unit : force.units()) {
            Optional<Combatant> upgraded = upgraded(seat, unit, alias);
            changed |= upgraded.isPresent();
            units.add(upgraded.orElse(unit));
        }
        return changed ? Optional.of(force.withUnits(units)) : Optional.empty();
    }

    private static Optional<Combatant> upgraded(Player seat, Combatant unit, String alias) {
        if (unit.type() == null) return Optional.empty();
        UnitModel current = seat.getUnitByType(unit.type());
        if (current == null || current.getUpgradesToUnitId().isEmpty()) return Optional.empty();
        UnitModel next = Mapper.getUnit(current.getUpgradesToUnitId().get());
        if (next == null || next.getRequiredTechId().filter(alias::equals).isEmpty()) return Optional.empty();
        int hitsOn = unit.hitsOn() + next.getCombatHitsOn() - current.getCombatHitsOn();
        int dice = unit.dice() + next.getCombatDieCount() - current.getCombatDieCount();
        if (hitsOn == unit.hitsOn() && dice == unit.dice()) return Optional.empty();
        return Optional.of(unit.rolling(hitsOn, dice));
    }

    private static List<String> copyable(Game game, Player seat, Player opponent) {
        return ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(seat, opponent, new ArrayList<>(), game);
    }

    private static double win(Force own, Force enemy, boolean seatAttacks) {
        return seatAttacks
                ? CombatOdds.resolve(own, enemy).attackerWins()
                : CombatOdds.resolve(enemy, own).defenderWins();
    }

    private static double stake(Force own, Force enemy) {
        return Stream.concat(own.units().stream(), enemy.units().stream())
                .mapToDouble(Combatant::cost)
                .sum();
    }

    private static Map<UnitType, Integer> unitsOf(UnitHolder holder, Player player, Iterable<UnitType> types) {
        Map<UnitType, Integer> units = new java.util.EnumMap<>(UnitType.class);
        for (UnitType type : types) {
            int count = BoardView.count(holder, player, type);
            if (count > 0) units.put(type, count);
        }
        return units;
    }

    private static Map<UnitType, Integer> damaged(UnitHolder holder, Side side) {
        Map<UnitType, Integer> damaged = new java.util.EnumMap<>(UnitType.class);
        side.units().forEach((type, count) -> {
            int hurt = Math.min(count, BoardView.damaged(holder, side.player(), type));
            if (hurt > 0) damaged.put(type, hurt);
        });
        return damaged;
    }

    private static List<Combatant> secondCancelledHits(List<Combatant> combatants) {
        long sustaining = combatants.stream().filter(Combatant::sustain).count();
        return Stream.generate(() -> new Combatant(NEVER_HITS, 0, false, 0))
                .limit(sustaining)
                .toList();
    }
}
