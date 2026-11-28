package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.HitCancelCards.Canceller;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.DecidingSeat;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

public record CombatHand(int moraleBoosts, int shieldsHolding, int jets, int directHits, boolean fighterPrototype) {

    public static final CombatHand NONE = new CombatHand(0, 0, 0, 0, false);
    private static final int SHIELDS_HOLDING_CANCELS = 2;
    private static final int MORALE_BOOST_BONUS = 1;
    private static final int FIGHTER_PROTOTYPE_BONUS = 2;
    private static final Combatant SHIELD = new Combatant(11, 0, false, 0);

    public static CombatHand of(Game game, Player seat) {
        if (!AiSettings.isCombatCardsEnabled()
                || !DecidingSeat.knowsHandOf(seat)
                || !ActionCardRules.mayPlayNow(game, seat)) {
            return NONE;
        }
        Set<String> held = seat.getActionCards().keySet();
        return new CombatHand(
                count(held, CombatCards.MORALE_BOOSTS),
                (int) Canceller.SHIELDS_HOLDING.copiesHeldBy(seat),
                (int) Canceller.JETS_AGAINST_SHIPS.copiesHeldBy(seat),
                count(held, DirectHitCards.ALIASES),
                held.contains(CombatCards.FIGHTER_PROTOTYPE));
    }

    private static int count(Set<String> held, Collection<String> aliases) {
        return (int) aliases.stream().filter(held::contains).count();
    }

    public boolean isEmpty() {
        return equals(NONE);
    }

    public CombatHand afterSpaceCombat() {
        return new CombatHand(Math.max(0, moraleBoosts - 1), 0, jets, 0, false);
    }

    public CombatHand afterJets() {
        return new CombatHand(moraleBoosts, shieldsHolding, Math.max(0, jets - 1), directHits, fighterPrototype);
    }

    public double[] cancellingCannon(double[] hits) {
        if (jets == 0 || hits.length < 2) return hits;
        double[] cancelled = new double[hits.length - 1];
        cancelled[0] = hits[0];
        for (int count = 1; count < hits.length; count++) cancelled[count - 1] += hits[count];
        return cancelled;
    }

    public boolean cancelsAny(double[] hits) {
        return jets > 0 && hits.length > 1 && hits[0] < 1;
    }

    public List<Combatant> firstSpaceRound(List<Combatant> units) {
        int allDice = units.stream().mapToInt(Combatant::dice).sum();
        int fighterDice = units.stream()
                .filter(unit -> unit.type() == UnitType.Fighter)
                .mapToInt(Combatant::dice)
                .sum();
        boolean prototype =
                fighterPrototype && fighterDice * FIGHTER_PROTOTYPE_BONUS > (moraleBoosts > 0 ? allDice : 0);
        if (prototype) return boosted(units, FIGHTER_PROTOTYPE_BONUS, true);
        return moraleBoosts > 0 ? boosted(units, MORALE_BOOST_BONUS, false) : units;
    }

    public List<Combatant> firstGroundRound(List<Combatant> units) {
        return moraleBoosts > 0 ? boosted(units, MORALE_BOOST_BONUS, false) : units;
    }

    private static List<Combatant> boosted(List<Combatant> units, int bonus, boolean fightersOnly) {
        return units.stream()
                .map(unit -> fightersOnly && unit.type() != UnitType.Fighter
                        ? unit
                        : unit.rolling(unit.hitsOn() - bonus, unit.dice()))
                .toList();
    }

    public List<Combatant> shielded(List<Combatant> units) {
        if (shieldsHolding == 0) return units;
        List<Combatant> shielded = new ArrayList<>(units);
        for (int shield = 0; shield < shieldsHolding * SHIELDS_HOLDING_CANCELS; shield++) shielded.add(SHIELD);
        return shielded;
    }

    public List<Combatant> directHit(Player opponent, List<Combatant> defenders) {
        if (directHits == 0) return defenders;
        List<Combatant> struck = new ArrayList<>(defenders);
        IntStream.range(0, struck.size())
                .filter(index -> struck.get(index).sustain() && canBeDirectHit(opponent, struck.get(index)))
                .boxed()
                .sorted(Comparator.comparingDouble(index -> -struck.get(index).cost()))
                .limit(directHits)
                .forEach(index -> struck.set(index, struck.get(index).withoutSustain()));
        return struck;
    }

    private static boolean canBeDirectHit(Player opponent, Combatant unit) {
        return unit.type() != null
                && BoardView.model(opponent, unit.type())
                        .map(UnitModel::getCanBeDirectHit)
                        .orElse(false);
    }
}
