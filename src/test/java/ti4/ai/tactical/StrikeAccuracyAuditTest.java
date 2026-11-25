package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Audit of StrikeEvaluator.estimate against an independent exact model of the same fight. The reference rolls
// anti-fighter
// barrage and bombardment as full dice distributions (not rounded expected hits), runs every space combat round to the
// end, applies capacity after the combat the way LRR 16.3a allows (the attacker removes fighters before ground forces),
// and then resolves the ground combat exactly. Unit values in the reference are typed in from the unit cards, so they
// also check the estimator's unit stats and faction modifiers. Numbers are printed, and the estimator must match the
// reference within SPACE_TOLERANCE on the space win and TAKE_TOLERANCE on the take.
class StrikeAccuracyAuditTest extends BaseTi4Test {

    private static final String HACAN_ID = "200000000000000003";
    private static final String XXCHA_ID = "200000000000000004";
    private static final int MAX_ROUNDS = 400;
    private static final double SPACE_TOLERANCE = 0.02;
    private static final double TAKE_TOLERANCE = 0.03;
    private static boolean bombardOff;
    private final SoftAssertions softly = new SoftAssertions();

    private record U(
            UnitType type,
            int hitsOn,
            int dice,
            boolean sustain,
            double cost,
            int capacity,
            int afbOn,
            int afbDice,
            int bombOn,
            int bombDice) {

        boolean fighter() {
            return type == UnitType.Fighter;
        }
    }

    private static U ship(UnitType type, int hitsOn, int dice, boolean sustain, double cost, int capacity) {
        return new U(type, hitsOn, dice, sustain, cost, capacity, 0, 0, 0, 0);
    }

    // --- vanilla (Hacan / Xxcha, no unit upgrades) ---
    private static U carrier(int hitsOn) {
        return ship(UnitType.Carrier, hitsOn, 1, false, 3, 4);
    }

    private static U fighter(int hitsOn) {
        return ship(UnitType.Fighter, hitsOn, 1, false, 0.5, 0);
    }

    private static U cruiser(int hitsOn) {
        return ship(UnitType.Cruiser, hitsOn, 1, false, 2, 0);
    }

    private static U destroyer(int hitsOn) {
        return new U(UnitType.Destroyer, hitsOn, 1, false, 1, 0, 9, 2, 0, 0);
    }

    private static U dreadnought(int hitsOn) {
        return new U(UnitType.Dreadnought, hitsOn, 1, true, 4, 1, 0, 0, 5, 1);
    }

    // Neutral unit reference card: dreadnought 5 with sustain, destroyer 8 with barrage 6 (x3).
    private static U neutralDreadnought() {
        return new U(UnitType.Dreadnought, 5, 1, true, 4, 0, 0, 0, 5, 1);
    }

    private static U neutralDestroyer() {
        return new U(UnitType.Destroyer, 8, 1, false, 1, 0, 6, 3, 0, 0);
    }

    private static List<U> many(int count, U unit) {
        List<U> units = new ArrayList<>();
        for (int i = 0; i < count; i++) units.add(unit);
        return units;
    }

    @SafeVarargs
    private static List<U> fleet(List<U>... groups) {
        List<U> all = new ArrayList<>();
        for (List<U> group : groups) all.addAll(group);
        return all;
    }

    // ===================== exact reference =====================

    // Hit points as an ordered list of events: each hit either sustains or destroys a unit. sustainFirst is the usual
    // player choice (all sustains, then cheapest first); neutral order assigns each hit to the cheapest unit left
    // (neutral-units note VII), so a neutral destroyer dies before the dreadnoughts sustain.
    private static final class Model {
        final List<U> units;
        final int hp;
        final double[][] dist;
        final List<boolean[]> aliveAt = new ArrayList<>();

        Model(List<U> raw, boolean sustainFirst) {
            this(raw, sustainFirst, false);
        }

        // guardCarriers: an attacker that lands ground forces takes its losses on other ships first, so its carriers
        // die last.
        Model(List<U> raw, boolean sustainFirst, boolean guardCarriers) {
            units = new ArrayList<>(raw);
            units.sort(Comparator.comparing((U unit) -> guardCarriers && unit.type() == UnitType.Carrier)
                    .thenComparingDouble(U::cost));
            List<int[]> events = new ArrayList<>();
            if (sustainFirst) {
                for (int i = 0; i < units.size(); i++) if (units.get(i).sustain()) events.add(new int[] {i, 0});
                for (int i = 0; i < units.size(); i++) events.add(new int[] {i, 1});
            } else {
                int i = 0;
                while (i < units.size()) {
                    int j = i;
                    while (j < units.size()
                            && units.get(j).cost() == units.get(i).cost()) j++;
                    for (int k = i; k < j; k++) if (units.get(k).sustain()) events.add(new int[] {k, 0});
                    for (int k = i; k < j; k++) events.add(new int[] {k, 1});
                    i = j;
                }
            }
            hp = events.size();
            dist = new double[hp + 1][];
            boolean[] alive = new boolean[units.size()];
            java.util.Arrays.fill(alive, true);
            for (int k = 0; k <= hp; k++) {
                if (k > 0 && events.get(k - 1)[1] == 1) alive[events.get(k - 1)[0]] = false;
                aliveAt.add(alive.clone());
                double[] d = {1};
                for (int u = 0; u < units.size(); u++) {
                    if (!alive[u]) continue;
                    double p = Math.clamp((11 - units.get(u).hitsOn()) / 10.0, 0, 1);
                    for (int die = 0; die < units.get(u).dice(); die++) d = addDie(d, p);
                }
                dist[k] = d;
            }
        }

        List<U> survivors(int absorbed) {
            List<U> left = new ArrayList<>();
            boolean[] alive = aliveAt.get(absorbed);
            for (int u = 0; u < units.size(); u++) if (alive[u]) left.add(units.get(u));
            return left;
        }
    }

    private static double[] addDie(double[] d, double p) {
        double[] r = new double[d.length + 1];
        for (int h = 0; h < d.length; h++) {
            r[h] += d[h] * (1 - p);
            r[h + 1] += d[h] * p;
        }
        return r;
    }

    private static double[] binomial(int dice, double p) {
        double[] d = {1};
        for (int i = 0; i < dice; i++) d = addDie(d, p);
        return d;
    }

    private record SpaceResult(double win, double lose, double both, double[] winAt) {}

    private static SpaceResult space(Model a, Model d) {
        double[][] p = new double[a.hp + 1][d.hp + 1];
        p[0][0] = 1;
        double win = 0, lose = 0, both = 0;
        double[] winAt = new double[a.hp + 1];
        for (int round = 0; round < MAX_ROUNDS; round++) {
            double[][] next = new double[a.hp + 1][d.hp + 1];
            double live = 0;
            for (int ka = 0; ka < a.hp; ka++) {
                for (int kd = 0; kd < d.hp; kd++) {
                    double s = p[ka][kd];
                    if (s == 0) continue;
                    double[] byA = a.dist[ka];
                    double[] byD = d.dist[kd];
                    for (int x = 0; x < byA.length; x++) {
                        for (int y = 0; y < byD.length; y++) {
                            double q = s * byA[x] * byD[y];
                            if (q == 0) continue;
                            int na = Math.min(a.hp, ka + y);
                            int nd = Math.min(d.hp, kd + x);
                            if (na == a.hp && nd == d.hp) both += q;
                            else if (nd == d.hp) {
                                win += q;
                                winAt[na] += q;
                            } else if (na == a.hp) lose += q;
                            else {
                                next[na][nd] += q;
                                live += q;
                            }
                        }
                    }
                }
            }
            p = next;
            if (live < 1e-12) break;
        }
        return new SpaceResult(win, lose, both, winAt);
    }

    private static List<U> withoutFighters(List<U> units, int killed) {
        List<U> left = new ArrayList<>();
        int toKill = killed;
        for (U unit : units) {
            if (unit.fighter() && toKill > 0) {
                toKill--;
                continue;
            }
            left.add(unit);
        }
        return left;
    }

    private static double[] afbHits(List<U> shooters) {
        double[] d = {1};
        for (U u : shooters) {
            for (int i = 0; i < u.afbDice(); i++) d = addDie(d, (11 - u.afbOn()) / 10.0);
        }
        return d;
    }

    private record Reference(double spaceWin, double take, double spaceWinRoundedAfb) {}

    // Exact space win and exact take (space win, then capacity, bombardment, ground combat) with barrage as a full
    // distribution; also the space win when barrage is replaced by its rounded expected hits, as the estimator does.
    // The attacker may take its losses on other ships to keep its carriers for the landing; it does so only when that
    // takes the planet more often.
    private static Reference reference(
            List<U> attackers,
            int infantry,
            int infantryHitsOn,
            List<U> defenders,
            int defenderInfantry,
            boolean neutralOrder) {
        Reference plain =
                reference(attackers, infantry, infantryHitsOn, defenders, defenderInfantry, neutralOrder, false);
        if (infantry == 0) return plain;
        Reference guarded =
                reference(attackers, infantry, infantryHitsOn, defenders, defenderInfantry, neutralOrder, true);
        return guarded.take() > plain.take() ? guarded : plain;
    }

    private static Reference reference(
            List<U> attackers,
            int infantry,
            int infantryHitsOn,
            List<U> defenders,
            int defenderInfantry,
            boolean neutralOrder,
            boolean guardCarriers) {
        double[] onDefenders = afbHits(attackers);
        double[] onAttackers = afbHits(defenders);
        double spaceWin = 0;
        double take = 0;
        for (int i = 0; i < onDefenders.length; i++) {
            for (int j = 0; j < onAttackers.length; j++) {
                double branch = onDefenders[i] * onAttackers[j];
                if (branch == 0) continue;
                Model a = new Model(withoutFighters(attackers, j), true, guardCarriers);
                Model d = new Model(withoutFighters(defenders, i), !neutralOrder);
                SpaceResult result = space(a, d);
                spaceWin += branch * result.win();
                for (int k = 0; k <= a.hp; k++) {
                    if (result.winAt()[k] == 0) continue;
                    take += branch
                            * result.winAt()[k]
                            * groundTake(a.survivors(k), infantry, infantryHitsOn, defenderInfantry);
                }
            }
        }
        double rounded = spaceWinRounded(attackers, defenders, neutralOrder);
        return new Reference(spaceWin, take, rounded);
    }

    private static double spaceWinRounded(List<U> attackers, List<U> defenders, boolean neutralOrder) {
        int onDefenders = (int) Math.round(expected(afbHits(attackers)));
        int onAttackers = (int) Math.round(expected(afbHits(defenders)));
        return space(
                        new Model(withoutFighters(attackers, onAttackers), true),
                        new Model(withoutFighters(defenders, onDefenders), !neutralOrder))
                .win();
    }

    private static double expected(double[] d) {
        double e = 0;
        for (int h = 0; h < d.length; h++) e += h * d[h];
        return e;
    }

    private static double groundTake(List<U> ships, int infantry, int infantryHitsOn, int defenders) {
        int capacity = ships.stream().mapToInt(U::capacity).sum();
        int landers = Math.min(infantry, capacity);
        if (landers == 0) return 0;
        double[] bombard = {1};
        for (U ship : ships) {
            for (int i = 0; i < (bombardOff ? 0 : ship.bombDice()); i++)
                bombard = addDie(bombard, (11 - ship.bombOn()) / 10.0);
        }
        double take = 0;
        for (int killed = 0; killed < bombard.length; killed++) {
            int left = Math.max(0, defenders - killed);
            take += bombard[killed] * groundWin(landers, infantryHitsOn, left, 8);
        }
        return take;
    }

    private static double groundWin(int ours, int ourHitsOn, int theirs, int theirHitsOn) {
        if (theirs == 0) return 1;
        double[][] p = new double[ours + 1][theirs + 1];
        p[ours][theirs] = 1;
        double win = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            double[][] next = new double[ours + 1][theirs + 1];
            double live = 0;
            for (int a = 1; a <= ours; a++) {
                for (int d = 1; d <= theirs; d++) {
                    double s = p[a][d];
                    if (s == 0) continue;
                    double[] byA = binomial(a, (11 - ourHitsOn) / 10.0);
                    double[] byD = binomial(d, (11 - theirHitsOn) / 10.0);
                    for (int x = 0; x < byA.length; x++) {
                        for (int y = 0; y < byD.length; y++) {
                            double q = s * byA[x] * byD[y];
                            int na = Math.max(0, a - y);
                            int nd = Math.max(0, d - x);
                            if (nd == 0 && na > 0) win += q;
                            else if (nd > 0 && na > 0) {
                                next[na][nd] += q;
                                live += q;
                            }
                        }
                    }
                }
            }
            p = next;
            if (live < 1e-12) break;
        }
        return win;
    }

    // ===================== estimator side =====================

    private record Ship(UnitType type, int count) {}

    private static Ship s(UnitType type, int count) {
        return new Ship(type, count);
    }

    private StrikeEstimate estimate(
            FractureScenario scenario, Player seat, Tile target, Player opponent, Ship... fleet) {
        Tile origin = scenario.board.staging;
        List<UnitMove> moves = new ArrayList<>();
        for (Ship ship : fleet) {
            scenario.board.test.units(origin, "space", seat, ship.type(), ship.count());
            moves.add(new UnitMove(origin.getPosition(), "space", ship.type(), ship.count()));
        }
        Strike strike = Strike.of(seat, target, opponent, List.of(origin), moves, StyxTake.RULES);
        StrikeEstimate estimate = StrikeEvaluator.estimate(scenario.board.test.game, seat, strike);
        for (Ship ship : fleet)
            origin.removeUnit("space", ti4.helpers.Units.getUnitKey(ship.type(), seat.getColor()), ship.count());
        return estimate;
    }

    private static String combatants(
            FractureScenario scenario, Player seat, Tile target, Player opponent, Ship... fleet) {
        java.util.Map<UnitType, Integer> ships = new java.util.EnumMap<>(UnitType.class);
        for (Ship ship : fleet)
            if (!BoardView.GROUND_FORCES.contains(ship.type())) ships.merge(ship.type(), ship.count(), Integer::sum);
        List<Combatant> list = CombatForces.combatants(
                scenario.board.test.game,
                target,
                BoardView.space(target),
                new Side(seat, ships),
                new Side(opponent, BoardView.ships(BoardView.space(target), opponent)),
                java.util.Map.of());
        StringBuilder text = new StringBuilder();
        for (Combatant c : list) {
            text.append(c.type())
                    .append('@')
                    .append(c.hitsOn())
                    .append('x')
                    .append(c.dice())
                    .append(c.sustain() ? "s" : "")
                    .append(' ');
        }
        return text.toString().trim();
    }

    private static String row(String name, StrikeEstimate est, Reference ref, Reference refNeutralOrder) {
        double gWin = est.groundWins()
                .getOrDefault(
                        "styx",
                        est.groundWins().isEmpty()
                                ? 0.0
                                : est.groundWins().values().iterator().next());
        return String.format(
                Locale.ROOT,
                "AUDIT %-38s est sWin=%.3f est gWin=%.3f est take=%.3f | exact sWin=%.3f take=%.3f | sWin(roundedAFB)=%.3f | neutralOrder sWin=%.3f take=%.3f | sent=%s",
                name,
                est.spaceWin(),
                gWin,
                est.spaceWin() * gWin,
                ref.spaceWin(),
                ref.take(),
                ref.spaceWinRoundedAfb(),
                refNeutralOrder == null ? Double.NaN : refNeutralOrder.spaceWin(),
                refNeutralOrder == null ? Double.NaN : refNeutralOrder.take(),
                est.groundSent());
    }

    private static double take(StrikeEstimate est) {
        return est.spaceWin() * est.groundWins().getOrDefault("styx", 0.0);
    }

    private void assertMatches(String name, StrikeEstimate est, Reference ref) {
        softly.assertThat(est.spaceWin()).as(name + " space win").isCloseTo(ref.spaceWin(), within(SPACE_TOLERANCE));
        softly.assertThat(take(est)).as(name + " take").isCloseTo(ref.take(), within(TAKE_TOLERANCE));
    }

    private void assertSpaceMatches(String name, StrikeEstimate est, Reference ref) {
        softly.assertThat(est.spaceWin()).as(name + " space win").isCloseTo(ref.spaceWin(), within(SPACE_TOLERANCE));
    }

    private static List<U> styxDefence() {
        return fleet(many(2, neutralDreadnought()), many(1, neutralDestroyer()));
    }

    @Test
    void styxCandidatesAgainstTheExactReference() {
        FractureScenario scenario = new FractureScenario();
        Player neutral = scenario.board.test.game.getNeutral();
        Tile styx = scenario.fracture(FractureScenario.STYX);
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");
        Player sardakk = scenario.board.sardakk;

        System.out.println("AUDIT neutral at Styx: "
                + combatants(scenario, neutral, styx, hacan, s(UnitType.Dreadnought, 2), s(UnitType.Destroyer, 1)));
        System.out.println(String.format(
                Locale.ROOT,
                "AUDIT exact ground 4 inf vs 3 inf at 8: %.3f, 3 vs 3: %.3f",
                groundWin(4, 8, 3, 8),
                groundWin(3, 8, 3, 8)));

        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "1 CV + 4 inf",
                fleet(many(1, carrier(9))),
                4,
                8,
                s(UnitType.Carrier, 1),
                s(UnitType.Infantry, 4));
        audit(scenario, hacan, styx, neutral, "1 CR", fleet(many(1, cruiser(7))), 0, 8, s(UnitType.Cruiser, 1));
        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "1 CV + 4 FF",
                fleet(many(1, carrier(9)), many(4, fighter(9))),
                0,
                8,
                s(UnitType.Carrier, 1),
                s(UnitType.Fighter, 4));
        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "2 CV + 6 FF + 1 DD + 2 inf",
                fleet(many(2, carrier(9)), many(6, fighter(9)), many(1, destroyer(9))),
                2,
                8,
                s(UnitType.Carrier, 2),
                s(UnitType.Fighter, 6),
                s(UnitType.Destroyer, 1),
                s(UnitType.Infantry, 2));
        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "1 DN + 1 CV + 4 FF + 1 inf",
                fleet(many(1, dreadnought(5)), many(1, carrier(9)), many(4, fighter(9))),
                1,
                8,
                s(UnitType.Dreadnought, 1),
                s(UnitType.Carrier, 1),
                s(UnitType.Fighter, 4),
                s(UnitType.Infantry, 1));
        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "2 DN + 2 CV + 6 FF + 4 inf",
                fleet(many(2, dreadnought(5)), many(2, carrier(9)), many(6, fighter(9))),
                4,
                8,
                s(UnitType.Dreadnought, 2),
                s(UnitType.Carrier, 2),
                s(UnitType.Fighter, 6),
                s(UnitType.Infantry, 4));
        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "3 DN + 1 CV + 2 FF + 3 inf",
                fleet(many(3, dreadnought(5)), many(1, carrier(9)), many(2, fighter(9))),
                3,
                8,
                s(UnitType.Dreadnought, 3),
                s(UnitType.Carrier, 1),
                s(UnitType.Fighter, 2),
                s(UnitType.Infantry, 3));
        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "3 CV + 10 FF + 2 inf",
                fleet(many(3, carrier(9)), many(10, fighter(9))),
                2,
                8,
                s(UnitType.Carrier, 3),
                s(UnitType.Fighter, 10),
                s(UnitType.Infantry, 2));

        // Sardakk: Unrelenting +1 on every combat roll, flagship +1 to the other ships (flagship 6x2 -> 5, others -2).
        U flagship = new U(UnitType.Flagship, 5, 2, true, 8, 3, 0, 0, 0, 0);
        audit(
                scenario,
                sardakk,
                styx,
                neutral,
                "Sardakk FS + 1 CV + 4 FF + 3 inf",
                fleet(many(1, flagship), many(1, carrier(7)), many(4, fighter(7))),
                3,
                7,
                s(UnitType.Flagship, 1),
                s(UnitType.Carrier, 1),
                s(UnitType.Fighter, 4),
                s(UnitType.Infantry, 3));
        audit(
                scenario,
                sardakk,
                styx,
                neutral,
                "Sardakk FS + 2 CV + 6 FF + 4 inf",
                fleet(many(1, flagship), many(2, carrier(7)), many(6, fighter(7))),
                4,
                7,
                s(UnitType.Flagship, 1),
                s(UnitType.Carrier, 2),
                s(UnitType.Fighter, 6),
                s(UnitType.Infantry, 4));

        hacan.addTech("ff2");
        audit(
                scenario,
                hacan,
                styx,
                neutral,
                "FF II: 2 CV + 6 FF + 2 inf",
                fleet(many(2, carrier(9)), many(6, fighter(8))),
                2,
                8,
                s(UnitType.Carrier, 2),
                s(UnitType.Fighter, 6),
                s(UnitType.Infantry, 2));
        softly.assertAll();
    }

    private void audit(
            FractureScenario scenario,
            Player seat,
            Tile target,
            Player opponent,
            String name,
            List<U> ships,
            int infantry,
            int infantryHitsOn,
            Ship... fleet) {
        System.out.println("AUDIT   " + name + " rated as: " + combatants(scenario, seat, target, opponent, fleet));
        StrikeEstimate est = estimate(scenario, seat, target, opponent, fleet);
        Reference ref = reference(ships, infantry, infantryHitsOn, styxDefence(), 3, false);
        Reference neutralOrder = reference(ships, infantry, infantryHitsOn, styxDefence(), 3, true);
        System.out.println(row(name, est, ref, neutralOrder));
        assertMatches(name, est, ref);
        bombardOff = true;
        Reference noBombard = reference(ships, infantry, infantryHitsOn, styxDefence(), 3, false);
        bombardOff = false;
        System.out.println(
                String.format(Locale.ROOT, "AUDIT     %s exact take without bombardment=%.3f", name, noBombard.take()));
    }

    @Test
    void carrierGroupAgainstALoneDreadnoughtAndASustainBarrageFight() {
        FractureScenario scenario = new FractureScenario();
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");
        Player xxcha = scenario.board.test.addSeat(XXCHA_ID, "xxcha", "green");
        Tile target = scenario.board.test.place("47", scenario.board.beyondStaging());

        scenario.board.test.units(target, "space", xxcha, UnitType.Dreadnought, 1);
        StrikeEstimate a = estimate(scenario, hacan, target, xxcha, s(UnitType.Carrier, 1), s(UnitType.Fighter, 4));
        Reference refA =
                reference(fleet(many(1, carrier(9)), many(4, fighter(9))), 0, 8, many(1, dreadnought(5)), 0, false);
        System.out.println(row("(a) 1 CV + 4 FF vs 1 DN", a, refA, null));
        assertSpaceMatches("(a)", a, refA);
        StrikeEstimate reverse = estimate(scenario, hacan, target, xxcha, s(UnitType.Dreadnought, 1));
        System.out.println(String.format(Locale.ROOT, "AUDIT (a') 1 DN vs 1 DN est sWin=%.3f", reverse.spaceWin()));
        assertThat(refA.spaceWin()).isGreaterThan(0.6);
        target.removeUnit("space", ti4.helpers.Units.getUnitKey(UnitType.Dreadnought, xxcha.getColor()), 1);

        scenario.board.test.units(target, "space", xxcha, UnitType.Dreadnought, 1);
        scenario.board.test.units(target, "space", xxcha, UnitType.Destroyer, 1);
        scenario.board.test.units(target, "space", xxcha, UnitType.Carrier, 1);
        scenario.board.test.units(target, "space", xxcha, UnitType.Fighter, 3);
        System.out.println("AUDIT   (c) xxcha rated as: "
                + combatants(
                        scenario,
                        xxcha,
                        target,
                        hacan,
                        s(UnitType.Dreadnought, 1),
                        s(UnitType.Destroyer, 1),
                        s(UnitType.Carrier, 1),
                        s(UnitType.Fighter, 3)));
        StrikeEstimate c = estimate(
                scenario,
                hacan,
                target,
                xxcha,
                s(UnitType.Dreadnought, 1),
                s(UnitType.Destroyer, 1),
                s(UnitType.Carrier, 1),
                s(UnitType.Fighter, 4));
        Reference refC = reference(
                fleet(many(1, dreadnought(5)), many(1, destroyer(9)), many(1, carrier(9)), many(4, fighter(9))),
                0,
                8,
                fleet(many(1, dreadnought(5)), many(1, destroyer(9)), many(1, carrier(9)), many(3, fighter(9))),
                0,
                false);
        System.out.println(row("(c) DN+DD+CV+4FF vs DN+DD+CV+3FF", c, refC, null));
        assertSpaceMatches("(c)", c, refC);

        StrikeEstimate c2 = estimate(
                scenario,
                hacan,
                target,
                xxcha,
                s(UnitType.Dreadnought, 1),
                s(UnitType.Destroyer, 2),
                s(UnitType.Carrier, 1),
                s(UnitType.Fighter, 4));
        Reference refC2 = reference(
                fleet(many(1, dreadnought(5)), many(2, destroyer(9)), many(1, carrier(9)), many(4, fighter(9))),
                0,
                8,
                fleet(many(1, dreadnought(5)), many(1, destroyer(9)), many(1, carrier(9)), many(3, fighter(9))),
                0,
                false);
        System.out.println(row("(c2) DN+2DD+CV+4FF vs DN+DD+CV+3FF", c2, refC2, null));
        assertSpaceMatches("(c2)", c2, refC2);
        softly.assertAll();
    }
}
