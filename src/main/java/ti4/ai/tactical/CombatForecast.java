package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SystemStake;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

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

    public static double extraDieSwing(Game game, Player seat, String position, String holderName) {
        return duel(game, seat, position, holderName).map(Duel::extraDieSwing).orElse(0.0);
    }

    public static double repairSwing(Game game, Player seat, String position, String holderName) {
        return duel(game, seat, position, holderName).map(Duel::repairSwing).orElse(0.0);
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

        double extraDieSwing() {
            Force own = own();
            Optional<Integer> best = IntStream.range(0, own.units().size())
                    .filter(index -> own.units().get(index).type() != null)
                    .boxed()
                    .min(Comparator.comparingInt(index -> own.units().get(index).hitsOn()));
            if (best.isEmpty()) return 0;
            List<Combatant> units = new ArrayList<>(own.units());
            Combatant unit = units.get(best.get());
            units.set(best.get(), unit.rolling(unit.hitsOn(), unit.dice() + 1));
            return swing(own, enemy(), own.withUnits(units));
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
