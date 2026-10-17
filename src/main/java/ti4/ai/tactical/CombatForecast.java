package ti4.ai.tactical;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Detailed;
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

    public static double repairSwing(Game game, Player seat, String position, String holderName) {
        return duel(game, seat, position, holderName).map(Duel::repairSwing).orElse(0.0);
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

        double firstRoundSwing(RoundModifier modifier) {
            Force own = own();
            Force enemy = enemy();
            Force boosted = own.withUnits(
                    own.units().stream().map(unit -> boosted(unit, modifier)).toList());
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
