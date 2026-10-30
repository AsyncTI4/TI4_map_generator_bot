package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Detailed;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.PlanetStake;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SystemStake;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class CombatForecast {

    public record RoundModifier(int allUnits, int fighters) {

        public static RoundModifier allUnits(int bonus) {
            return new RoundModifier(bonus, 0);
        }

        public static RoundModifier fighters(int bonus) {
            return new RoundModifier(0, bonus);
        }

        int bonusFor(UnitType type) {
            return allUnits + (type == UnitType.Fighter ? fighters : 0);
        }
    }

    public static double firstRoundSwing(
            Game game, Player seat, String position, String holderName, RoundModifier modifier) {
        return duel(game, seat, position, holderName)
                .map(duel -> duel.firstRoundSwing(modifier))
                .orElse(0.0);
    }

    public static OptionalDouble lossChance(Game game, Player seat, String position, String holderName) {
        return duel(game, seat, position, holderName)
                .map(Duel::lossChance)
                .map(OptionalDouble::of)
                .orElse(OptionalDouble.empty());
    }

    public static double repairSwing(Game game, Player seat, String position, String holderName) {
        return duel(game, seat, position, holderName).map(Duel::repairSwing).orElse(0.0);
    }

    public record ShipLoss(double winBefore, double winAfter, double stake) {

        public double swing() {
            return (winAfter - winBefore) * stake;
        }
    }

    public static Optional<ShipLoss> enemyShipLoss(
            Game game, Player seat, String position, Player opponent, UnitType type) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !BoardView.hasOwnShips(seat, tile) || !BoardView.hasOwnShips(opponent, tile)) {
            return Optional.empty();
        }
        return new Duel(game, seat, opponent, tile, BoardView.space(tile), false).enemyShipLoss(type);
    }

    public record SpaceOutlook(double win, double ourLoss, double theirLoss) {}

    public static Optional<SpaceOutlook> spaceOutlook(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !BoardView.hasOwnShips(seat, tile)) return Optional.empty();
        UnitHolder space = BoardView.space(tile);
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.hasOwnShips(other, tile))
                .findFirst()
                .map(opponent -> {
                    Force own = CombatForces.current(game, tile, space, seat, opponent, false);
                    Force enemy = CombatForces.current(game, tile, space, opponent, seat, false);
                    Detailed detailed = CombatOdds.resolveDetailed(own, enemy);
                    return new SpaceOutlook(
                            detailed.outcome().attackerWins(),
                            detailed.attackerCostLost(),
                            detailed.defenderCostLost());
                });
    }

    public static double landingWin(
            Game game, Player seat, String position, String planetName, Map<UnitType, Integer> landing) {
        Tile tile = game.getTileByPosition(position);
        UnitHolder planet = tile == null ? null : tile.getUnitHolders().get(planetName);
        if (planet == null) return 0;
        Optional<Player> defender = game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.groundForces(planet, other) > 0)
                .findFirst();
        if (defender.isEmpty()) return 1;
        Player opponent = defender.get();
        Map<UnitType, Integer> attackers = new EnumMap<>(UnitType.class);
        landing.forEach((type, count) -> attackers.merge(type, count, Integer::sum));
        int shotDown = (int) Math.round(BattleMath.spaceCannonHits(opponent, planet));
        for (UnitType type : BoardView.GROUND_FORCES) {
            int removed = Math.min(shotDown, attackers.getOrDefault(type, 0));
            if (removed > 0) attackers.merge(type, -removed, Integer::sum);
            shotDown -= removed;
        }
        BoardView.GROUND_FORCES.forEach(type -> {
            int present = BoardView.count(planet, seat, type);
            if (present > 0) attackers.merge(type, present, Integer::sum);
        });
        Side attacking = new Side(seat, attackers);
        Side defending = new Side(opponent, CombatForces.unitsOf(planet, opponent, BoardView.GROUND_FORCES));
        Map<UnitType, Integer> hurt = BattleMath.damagedAmong(planet, opponent, defending.units());
        Force defence = CombatForces.force(
                opponent, CombatForces.combatants(game, tile, planet, defending, attacking, hurt), true);
        Force attack = CombatForces.force(
                seat, CombatForces.combatants(game, tile, planet, attacking, defending, Map.of()), true);
        return CombatOdds.resolve(attack, defence).attackerWins();
    }

    public static boolean landingFacesSpaceCannon(Game game, Player seat, String position, Collection<String> planets) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return false;
        return planets.stream()
                .map(tile.getUnitHolders()::get)
                .filter(Objects::nonNull)
                .anyMatch(planet -> game.getRealPlayers().stream()
                        .anyMatch(other -> other != seat && BattleMath.spaceCannonHits(other, planet) > 0));
    }

    public record FirstRoundGain(double gain, double winChance) {

        public static final FirstRoundGain NONE = new FirstRoundGain(0, 0);
    }

    public static FirstRoundGain rerollGain(Game game, Player seat, String position) {
        return duel(game, seat, position, BoardView.SPACE)
                .map(duel -> duel.firstRoundGain(own -> own.rerollingMissesInFirstRounds(1)))
                .orElse(FirstRoundGain.NONE);
    }

    public static FirstRoundGain mechSwapGain(Game game, Player seat, String position, String planet) {
        return duel(game, seat, position, planet)
                .filter(Duel::ground)
                .map(Duel::mechSwapGain)
                .orElse(FirstRoundGain.NONE);
    }

    public record ExtraDie(double winWithout, double winWith) {

        public static final double DECIDED_WIN = 0.95;
        public static final double HOPELESS = 0.05;
        public static final ExtraDie NONE = new ExtraDie(0, 0);

        public boolean worthSpending() {
            return winWithout < DECIDED_WIN && winWith >= HOPELESS;
        }
    }

    public static ExtraDie extraDie(Game game, Player seat, String position, String holderName) {
        return duel(game, seat, position, holderName).map(Duel::extraDie).orElse(ExtraDie.NONE);
    }

    private static Optional<Duel> duel(Game game, Player seat, String position, String holderName) {
        Tile tile = game.getTileByPosition(position);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(holderName);
        if (holder == null) return Optional.empty();
        boolean ground = !BoardView.SPACE.equals(holderName);
        if (!present(seat, tile, holder, ground)) return Optional.empty();
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && present(other, tile, holder, ground))
                .findFirst()
                .map(opponent -> new Duel(game, seat, opponent, tile, holder, ground));
    }

    private static boolean present(Player player, Tile tile, UnitHolder holder, boolean ground) {
        return ground ? BoardView.groundForces(holder, player) > 0 : BoardView.hasOwnShips(player, tile);
    }

    private record Duel(Game game, Player seat, Player opponent, Tile tile, UnitHolder holder, boolean ground) {

        boolean attacks() {
            return game.getActivePlayer() == seat;
        }

        Force own() {
            return CombatForces.current(game, tile, holder, seat, opponent, ground);
        }

        Force enemy() {
            return CombatForces.current(game, tile, holder, opponent, seat, ground);
        }

        ExtraDie extraDie() {
            Force own = own().rerollingMissesInFirstRounds(0);
            Force enemy = enemy();
            return new ExtraDie(winOf(detailed(own, own, enemy)), winOf(detailed(own, withExtraDie(own), enemy)));
        }

        private Force withExtraDie(Force own) {
            List<Combatant> units = own.units();
            OptionalInt chosen = ExtraDieTarget.choose(units, ground);
            if (chosen.isEmpty()) return own;
            List<Combatant> boosted = new ArrayList<>(units);
            Combatant unit = units.get(chosen.getAsInt());
            boosted.set(chosen.getAsInt(), unit.rolling(unit.hitsOn(), unit.dice() + 1));
            return own.withUnits(boosted);
        }

        double firstRoundSwing(RoundModifier modifier) {
            Force own = own();
            Force boosted = own.withUnits(
                    own.units().stream().map(unit -> boosted(unit, modifier)).toList());
            return swing(own, enemy(), boosted);
        }

        private double swing(Force own, Force enemy, Force boosted) {
            double before = CombatForces.win(own, enemy, attacks());
            double after = attacks()
                    ? CombatOdds.resolveFirstRound(own, enemy, boosted, enemy).attackerWins()
                    : CombatOdds.resolveFirstRound(enemy, own, enemy, boosted).defenderWins();
            return (after - before) * stake(own, enemy);
        }

        Optional<ShipLoss> enemyShipLoss(UnitType type) {
            Force own = own();
            Force enemy = enemy();
            List<Combatant> remaining = new ArrayList<>(enemy.units());
            int index = IntStream.range(0, remaining.size())
                    .filter(i -> remaining.get(i).type() == type)
                    .reduce((first, second) -> remaining.get(second).sustain() ? first : second)
                    .orElse(-1);
            if (index < 0) return Optional.empty();
            remaining.remove(index);
            double before = CombatForces.win(own, enemy, attacks());
            double after = CombatForces.win(own, enemy.withUnits(remaining), attacks());
            return Optional.of(new ShipLoss(before, after, stake(own, enemy)));
        }

        double lossChance() {
            return 1 - CombatForces.win(own(), enemy(), attacks());
        }

        double repairSwing() {
            Side side = new Side(seat, unitsOf(seat));
            Side other = new Side(opponent, unitsOf(opponent));
            if (CombatForces.damaged(holder, side).isEmpty()) return 0;
            Force own = own();
            Force enemy = enemy();
            Force repaired = CombatForces.force(
                    seat, CombatForces.combatants(game, tile, holder, side, other, Map.of()), ground);
            double before = CombatForces.win(own, enemy, attacks());
            double after = CombatForces.win(repaired, enemy, attacks());
            return (after - before) * stake(own, enemy);
        }

        FirstRoundGain firstRoundGain(UnaryOperator<Force> firstRoundChange) {
            Force own = own().rerollingMissesInFirstRounds(0);
            Force enemy = enemy();
            return compare(own, enemy, 0, detailed(own, own, enemy), detailed(own, firstRoundChange.apply(own), enemy));
        }

        FirstRoundGain mechSwapGain() {
            Force own = own();
            Force enemy = enemy();
            Optional<Force> swapped = withMechInPlaceOfInfantry(own);
            if (swapped.isEmpty()) return FirstRoundGain.NONE;
            return compare(
                    own,
                    enemy,
                    planetStake(),
                    detailed(own, own, enemy),
                    detailed(swapped.get(), swapped.get(), enemy));
        }

        private FirstRoundGain compare(Force own, Force enemy, double extraStake, Detailed plain, Detailed changed) {
            double winBefore = winOf(plain);
            double swing = (winOf(changed) - winBefore) * (stake(own, enemy) + extraStake);
            double costs = netLoss(plain) - netLoss(changed);
            return new FirstRoundGain(swing + costs, winBefore);
        }

        private double planetStake() {
            return holder instanceof Planet planet ? PlanetStake.of(game, seat, planet) : 0;
        }

        private Optional<Force> withMechInPlaceOfInfantry(Force own) {
            UnitModel mech = seat.getUnitByType(UnitType.Mech);
            UnitModel infantry = seat.getUnitByType(UnitType.Infantry);
            List<Combatant> units = new ArrayList<>(own.units());
            int index = IntStream.range(0, units.size())
                    .filter(i -> units.get(i).type() == UnitType.Infantry)
                    .findFirst()
                    .orElse(-1);
            if (mech == null || infantry == null || index < 0) return Optional.empty();
            Combatant replaced = units.get(index);
            int hitsOn = mech.getCombatHitsOn() + replaced.hitsOn() - infantry.getCombatHitsOn();
            units.set(
                    index,
                    new Combatant(
                            hitsOn, mech.getCombatDieCount(), mech.getSustainDamage(), mech.getCost(), UnitType.Mech));
            return Optional.of(own.withUnits(units));
        }

        private Detailed detailed(Force own, Force firstRoundOwn, Force enemy) {
            return attacks()
                    ? CombatOdds.resolveDetailed(own, enemy, firstRoundOwn, enemy, 0, 0)
                    : CombatOdds.resolveDetailed(enemy, own, enemy, firstRoundOwn, 0, 0);
        }

        private double winOf(Detailed detailed) {
            return attacks()
                    ? detailed.outcome().attackerWins()
                    : detailed.outcome().defenderWins();
        }

        private double netLoss(Detailed detailed) {
            return attacks()
                    ? detailed.attackerCostLost() - detailed.defenderCostLost()
                    : detailed.defenderCostLost() - detailed.attackerCostLost();
        }

        private Map<UnitType, Integer> unitsOf(Player player) {
            return ground
                    ? CombatForces.unitsOf(holder, player, BoardView.GROUND_FORCES)
                    : BoardView.ships(holder, player);
        }

        private double stake(Force own, Force enemy) {
            double points = SystemStake.points(game, seat);
            return CombatForces.stake(own, enemy) + ScoringStakes.scoreValue(game, seat, points);
        }

        private static Combatant boosted(Combatant unit, RoundModifier modifier) {
            int bonus = unit.type() == null ? 0 : modifier.bonusFor(unit.type());
            return bonus == 0 ? unit : unit.rolling(unit.hitsOn() - bonus, unit.dice());
        }
    }
}
