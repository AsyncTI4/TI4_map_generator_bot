package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CombatHand;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CannonDice;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Detailed;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.CombatOdds.Outcome;
import ti4.ai.eval.DiceOdds;
import ti4.ai.eval.FighterWings;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.IconStake;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.PlanetStake;
import ti4.ai.letnev.L4Disruptors;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class StrikeEvaluator {

    private static final Detailed UNDEFENDED = new Detailed(new Outcome(1, 0, 0), 0, 0, 0, 0, 0);
    private static final String FORESIGHT = "foresight";
    private static final double FORESIGHT_ESCAPE_WIN = 0.5;
    private static final double COST_TOLERANCE = 1e-9;
    private static final double NEGLIGIBLE_BRANCH = 1e-6;
    private static final double[] NO_HITS = {1.0};

    private record Space(
            double win,
            double ourLoss,
            double ourLossIfWin,
            double theirLoss,
            double killChance,
            double ourLossChance,
            Map<Map<UnitType, Integer>, Double> wonWith) {}

    private record Ground(
            Map<String, Integer> landings,
            Map<String, Double> wins,
            Map<String, Integer> sent,
            boolean fought,
            double ourLoss,
            double theirLoss,
            double noKill,
            Map<UnitType, Integer> dropped) {}

    private record Branch(List<Combatant> attackers, List<Combatant> defenders) {}

    private record Arrival(double chance, Map<UnitType, Integer> ships, Map<UnitType, Integer> ground) {}

    private record GroundFight(Detailed detailed, double bombardedCost) {}

    private record PlanetOdds(double win, double ourLoss, double theirLoss, double killChance) {}

    private record Invasion(
            Game game,
            Tile tile,
            Planet planet,
            Player seat,
            Player opponent,
            Map<UnitType, Integer> defenders,
            CombatHand hand) {}

    // TODO: Mentak Ambush, L4 Disruptors and Titans' planetary PDS are not in the odds
    static StrikeEstimate estimate(Game game, Player seat, Strike strike) {
        return estimate(game, seat, strike, seat);
    }

    static StrikeEstimate estimate(Game game, Player seat, Strike strike, Player decider) {
        try (Thinking.Span timing = Thinking.measure("strike.estimate")) {
            CombatHand hand = decider == seat ? CombatHand.of(game, seat) : CombatHand.NONE;
            return estimate(game, seat, strike, true, hand);
        }
    }

    static StrikeEstimate laterSpaceCombat(Game game, Player seat, Strike strike) {
        return estimate(game, seat, strike, false, CombatHand.of(game, seat));
    }

    private static StrikeEstimate estimate(
            Game game, Player seat, Strike strike, boolean openingFire, CombatHand hand) {
        if (SkippedSpaceCombat.assumedFor(seat)
                && !strike.target().getPlanetUnitHolders().isEmpty()) {
            return skippingSpaceCombat(game, seat, strike, hand);
        }
        StrikeEstimate plain = estimate(game, seat, strike, openingFire, hand, false);
        if (!guardsTransports(game, seat, strike)) return plain;
        StrikeEstimate guarded = estimate(game, seat, strike, openingFire, hand, true);
        return takeChance(guarded) > takeChance(plain) ? guarded : plain;
    }

    private static double takeChance(StrikeEstimate estimate) {
        double ground = estimate.groundWins().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
        return estimate.spaceWin() * ground;
    }

    private static StrikeEstimate estimate(
            Game game, Player seat, Strike strike, boolean openingFire, CombatHand hand, boolean guardTransports) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        UnitHolder space = BoardView.space(target);
        Side attacking = new Side(seat, strike.ships());
        Map<UnitType, Integer> defenderShips = merged(BoardView.ships(space, opponent), strike.extraDefenderShips());
        boolean spaceCombat = !defenderShips.isEmpty();
        double[] theirCannon =
                openingFire ? DiceOdds.distribution(CannonDice.againstShipsOf(game, seat, target)) : NO_HITS;
        boolean jetsOnShips = hand.cancelsAny(theirCannon);
        if (jetsOnShips) theirCannon = hand.cancellingCannon(theirCannon);
        CombatHand afterCannon = jetsOnShips ? hand.afterJets() : hand;
        List<Combatant> arriving =
                CombatForces.combatants(game, target, space, attacking, new Side(opponent, Map.of()), strike.damaged());
        Space cannonOnly = cannonOnly(arriving, theirCannon);
        Space fight = spaceCombat
                ? spaceCombat(
                        game,
                        seat,
                        strike,
                        attacking,
                        new Side(opponent, defenderShips),
                        theirCannon,
                        openingFire,
                        afterCannon,
                        guardTransports)
                : cannonOnly;
        boolean escaped = spaceCombat && escapes(game, strike, fight);
        if (escaped) fight = cannonOnly;
        if (fight.win() == 0) return lost(seat, strike, fight, spaceCombat);
        Map<UnitType, Integer> survivors = survivors(seat, strike.ships(), fight.ourLossIfWin(), guardTransports);
        CombatHand groundHand = spaceCombat && !escaped ? afterCannon.afterSpaceCombat() : afterCannon;
        Ground ground = ground(game, seat, strike, fight, survivors, BoardView.ships(space, seat), groundHand, true);
        double cargoLoss =
                (1 - fight.win()) * shipCost(seat, strike.cargo()) + fight.win() * shipCost(seat, ground.dropped());
        return new StrikeEstimate(
                fight.win(),
                escaped,
                spaceCombat && !escaped,
                ground.fought(),
                ground.landings(),
                ground.wins(),
                ground.sent(),
                fight.ourLoss() + ground.ourLoss() + cargoLoss,
                fight.ourLossIfWin(),
                fight.theirLoss() + ground.theirLoss(),
                fight.killChance(),
                1 - ground.noKill(),
                fight.ourLossChance(),
                without(survivors, ground.dropped()));
    }

    private static Space cannonOnly(List<Combatant> arriving, double[] cannon) {
        double before = costOf(arriving);
        double[] hits = DiceOdds.capped(cannon, BattleMath.hitPoints(arriving));
        Map<Map<UnitType, Integer>, Double> wonWith = new HashMap<>();
        double win = 0;
        double loss = 0;
        double lossIfWin = 0;
        double lossChance = 0;
        for (int count = 0; count < hits.length; count++) {
            double chance = hits[count];
            if (chance == 0) continue;
            List<Combatant> left = BattleMath.afterCannonFire(arriving, count);
            double lost = before - costOf(left);
            loss += chance * lost;
            if (lost > 0) lossChance += chance;
            if (left.isEmpty() && !arriving.isEmpty()) continue;
            win += chance;
            lossIfWin += chance * lost;
            wonWith.merge(types(left), chance, Double::sum);
        }
        return new Space(win, loss, win == 0 ? 0 : lossIfWin / win, 0, 0, lossChance, wonWith);
    }

    private static StrikeEstimate lost(Player seat, Strike strike, Space fight, boolean spaceCombat) {
        double ourLoss = fight.ourLoss() + shipCost(seat, strike.cargo());
        return new StrikeEstimate(
                0,
                false,
                spaceCombat,
                false,
                Map.of(),
                Map.of(),
                Map.of(),
                ourLoss,
                fight.ourLossIfWin(),
                fight.theirLoss(),
                fight.killChance(),
                0,
                fight.ourLossChance(),
                Map.of());
    }

    private static boolean escapes(Game game, Strike strike, Space fight) {
        Player opponent = strike.opponent();
        Tile target = strike.target();
        if (!opponent.hasAbility(FORESIGHT) || opponent.getStrategicCC() <= 0) return false;
        boolean shipsPresent =
                !BoardView.ships(BoardView.space(target), opponent).isEmpty();
        if (!shipsPresent || fight.win() < FORESIGHT_ESCAPE_WIN) return false;
        for (String position : FoWHelper.getAdjacentTiles(game, target.getPosition(), opponent, false)) {
            Tile neighbour = game.getTileByPosition(position);
            if (neighbour == null || neighbour == target || !MovementGraph.canEnter(game, opponent, neighbour)) {
                continue;
            }
            if (!BoardView.hasEnemyShips(game, opponent, neighbour)) return true;
        }
        return false;
    }

    private static Space spaceCombat(
            Game game,
            Player seat,
            Strike strike,
            Side attacking,
            Side defending,
            double[] theirCannon,
            boolean openingFire,
            CombatHand hand,
            boolean guardTransports) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        UnitHolder space = BoardView.space(target);
        Map<UnitType, Integer> defendersDamaged = BattleMath.damagedAmong(space, opponent, defending.units());
        List<Combatant> defenders =
                CombatForces.combatants(game, target, space, defending, attacking, defendersDamaged);
        if (defenders.isEmpty()) return new Space(0, shipCost(seat, strike.ships()), 0, 0, 0, 1, Map.of());
        List<Combatant> attackers = guardingTransports(
                guardTransports, CombatForces.combatants(game, target, space, attacking, defending, strike.damaged()));
        double[] ownCannon = openingFire
                ? DiceOdds.distribution(BattleMath.ownCannonDice(game, seat, target, strike.origins()))
                : NO_HITS;
        double attackerCost = costOf(attackers);
        double defenderCost = costOf(defenders);
        Map<Map<UnitType, Integer>, Double> wonWith = new HashMap<>();
        double win = 0;
        double ourLoss = 0;
        double ourLossIfWin = 0;
        double theirLoss = 0;
        double killChance = 0;
        double ourLossChance = 0;
        for (Map.Entry<Branch, Double> entry : branches(seat, opponent, attackers, defenders, theirCannon, ownCannon)
                .entrySet()) {
            Branch branch = entry.getKey();
            double chance = entry.getValue();
            Force defence = CombatForces.force(opponent, hand.directHit(opponent, branch.defenders()), false);
            List<Combatant> shielded = hand.shielded(branch.attackers());
            Force attack = CombatForces.withExpectedCopy(
                    game, seat, opponent, CombatForces.force(seat, shielded, false), defence, true, false);
            Detailed detailed = CombatOdds.resolveDetailed(
                    attack, defence, firstRound(attack, hand.firstSpaceRound(attack.units())), defence, 0, 0);
            double preOurLoss = attackerCost - costOf(branch.attackers());
            double preTheirLoss = defenderCost - costOf(branch.defenders());
            double branchWin = detailed.outcome().attackerWins();
            win += chance * branchWin;
            ourLoss += chance * (preOurLoss + detailed.attackerCostLost());
            ourLossIfWin += chance * branchWin * (preOurLoss + detailed.attackerCostLostIfWin());
            theirLoss += chance * (preTheirLoss + detailed.defenderCostLost());
            killChance += chance * (preTheirLoss > 0 ? 1 : detailed.defenderLosesUnit());
            ourLossChance += chance * (preOurLoss > 0 ? 1 : detailed.attackerLosesUnit());
            addWins(wonWith, shielded, detailed.attackerWinsAt(), chance);
        }
        double groundInSpace = groundInSpaceCost(opponent, space);
        return new Space(
                win,
                ourLoss,
                win == 0 ? 0 : ourLossIfWin / win,
                theirLoss + win * groundInSpace,
                killChance,
                ourLossChance,
                wonWith);
    }

    private static Force firstRound(Force force, List<Combatant> boosted) {
        return boosted == force.units() ? force : force.withUnits(boosted);
    }

    private static Map<Branch, Double> branches(
            Player seat,
            Player opponent,
            List<Combatant> attackers,
            List<Combatant> defenders,
            double[] theirCannon,
            double[] ownCannon) {
        Map<Branch, Double> branches = new HashMap<>();
        double[] onAttackers = DiceOdds.pruned(DiceOdds.capped(theirCannon, BattleMath.hitPoints(attackers)));
        double[] onDefenders = DiceOdds.pruned(DiceOdds.capped(ownCannon, BattleMath.hitPoints(defenders)));
        for (int ours = 0; ours < onAttackers.length; ours++) {
            if (onAttackers[ours] == 0) continue;
            List<Combatant> shotAttackers = BattleMath.afterCannonFire(attackers, ours);
            for (int theirs = 0; theirs < onDefenders.length; theirs++) {
                if (onDefenders[theirs] == 0) continue;
                List<Combatant> shotDefenders = BattleMath.afterCannonFire(defenders, theirs);
                addBarrageBranches(
                        branches,
                        seat,
                        opponent,
                        shotAttackers,
                        shotDefenders,
                        onAttackers[ours] * onDefenders[theirs]);
            }
        }
        return branches;
    }

    private static void addBarrageBranches(
            Map<Branch, Double> branches,
            Player seat,
            Player opponent,
            List<Combatant> attackers,
            List<Combatant> defenders,
            double chance) {
        List<Combatant> cannonedAttackers = BattleMath.firesAssaultCannon(opponent, defenders)
                ? BattleMath.afterAssaultCannon(attackers)
                : attackers;
        List<Combatant> cannonedDefenders =
                BattleMath.firesAssaultCannon(seat, attackers) ? BattleMath.afterAssaultCannon(defenders) : defenders;
        double[] onDefenders = barrage(seat, cannonedAttackers, cannonedDefenders);
        double[] onAttackers = barrage(opponent, cannonedDefenders, cannonedAttackers);
        for (int theirFighters = 0; theirFighters < onDefenders.length; theirFighters++) {
            for (int ourFighters = 0; ourFighters < onAttackers.length; ourFighters++) {
                double branch = chance * onDefenders[theirFighters] * onAttackers[ourFighters];
                if (branch < NEGLIGIBLE_BRANCH) continue;
                branches.merge(
                        new Branch(
                                BattleMath.withoutFighters(cannonedAttackers, ourFighters),
                                BattleMath.withoutFighters(cannonedDefenders, theirFighters)),
                        branch,
                        Double::sum);
            }
        }
    }

    private static double[] barrage(Player shooter, List<Combatant> shooting, List<Combatant> targets) {
        int fighters = BattleMath.fighters(targets);
        if (fighters == 0) return NO_HITS;
        return DiceOdds.pruned(
                DiceOdds.capped(DiceOdds.distribution(BattleMath.barrageDice(shooter, shooting)), fighters));
    }

    private static void addWins(
            Map<Map<UnitType, Integer>, Double> wonWith, List<Combatant> attackers, double[] winsAt, double chance) {
        for (int absorbed = 0; absorbed < winsAt.length; absorbed++) {
            double won = chance * winsAt[absorbed];
            if (won <= 0) continue;
            wonWith.merge(types(CombatOdds.survivors(attackers, absorbed)), won, Double::sum);
        }
    }

    private static Map<UnitType, Integer> types(List<Combatant> combatants) {
        Map<UnitType, Integer> types = new EnumMap<>(UnitType.class);
        for (Combatant combatant : combatants) {
            if (combatant.type() != null) types.merge(combatant.type(), 1, Integer::sum);
        }
        return types;
    }

    private static List<Combatant> guardingTransports(boolean guard, List<Combatant> attackers) {
        if (!guard) return attackers;
        return attackers.stream()
                .map(unit -> unit.type() == UnitType.Carrier ? unit.guarding() : unit)
                .toList();
    }

    private static boolean guardsTransports(Game game, Player seat, Strike strike) {
        return count(strike.cargo()) > 0
                && strike.ships().getOrDefault(UnitType.Carrier, 0) > 0
                && !landingPlanets(game, seat, strike).isEmpty();
    }

    private static Map<UnitType, Integer> survivors(
            Player seat, Map<UnitType, Integer> ships, double lossIfWin, boolean guardTransports) {
        Map<UnitType, Integer> kept = new EnumMap<>(UnitType.class);
        kept.putAll(ships);
        List<UnitType> cheapestFirst = new ArrayList<>(kept.keySet());
        cheapestFirst.sort(Comparator.comparing((UnitType type) -> guardTransports && type == UnitType.Carrier)
                .thenComparingDouble(type -> unitCost(seat, type)));
        double budget = lossIfWin + COST_TOLERANCE;
        for (UnitType type : cheapestFirst) {
            double cost = unitCost(seat, type);
            while (kept.get(type) > 0 && cost <= budget) {
                kept.merge(type, -1, Integer::sum);
                budget -= cost;
            }
        }
        kept.values().removeIf(count -> count <= 0);
        return kept;
    }

    private static Ground ground(
            Game game,
            Player seat,
            Strike strike,
            Space fight,
            Map<UnitType, Integer> survivors,
            Map<UnitType, Integer> present,
            CombatHand hand,
            boolean bombardmentStep) {
        Tile target = strike.target();
        Player opponent = strike.opponent();
        StrikeRules rules = strike.rules();
        double spaceWin = fight.win();
        List<Planet> planets = landingPlanets(game, seat, strike);
        boolean landing = !planets.isEmpty();
        Optional<BombardmentRules.Target> bombardment = bombardmentStep
                ? BombardmentRules.target(game, seat, target, merged(present, strike.ships()))
                : Optional.empty();
        Map<UnitType, Integer> remaining = new EnumMap<>(UnitType.class);
        remaining.putAll(strike.cargo());
        Map<UnitType, Integer> dropped = dropExcessCargo(seat, remaining, survivors, landing, nonFighterCount(present));
        strike.pulled().forEach((type, count) -> remaining.merge(type, count, Integer::sum));
        List<Arrival> arrivals = arrivals(seat, strike, fight, survivors, present, landing);
        Map<String, GroundFight> fights = new HashMap<>();
        Map<String, Integer> landings = new LinkedHashMap<>();
        Map<String, Double> wins = new LinkedHashMap<>();
        Map<String, Integer> sentTo = new LinkedHashMap<>();
        double ourLoss = 0;
        double theirLoss = 0;
        double noKill = 1;
        boolean fought = false;
        Map<UnitType, Integer> extraGround = new EnumMap<>(UnitType.class);
        extraGround.putAll(strike.extraDefenderGround());
        for (Planet planet : planets) {
            int ground = count(remaining);
            if (ground == 0) continue;
            Map<UnitType, Integer> defenderGround = groundOn(planet, opponent);
            if (BoardView.controller(game, planet.getName()) == opponent || !defenderGround.isEmpty()) {
                extraGround.forEach((type, count) -> defenderGround.merge(type, count, Integer::sum));
                extraGround.clear();
            }
            int magenInfantry = MagenDefenseGrid.placedInfantry(opponent, planet);
            if (magenInfantry > 0) defenderGround.merge(UnitType.Infantry, magenInfantry, Integer::sum);
            int defenders = count(defenderGround);
            List<Double> cannonDice =
                    L4Disruptors.shieldsInvaders(seat) ? List.of() : BattleMath.spaceCannonDice(opponent, planet);
            int hits = (int) Math.round(
                    cannonDice.stream().mapToDouble(Double::doubleValue).sum());
            int sent = defenders == 0
                    ? Math.min(ground, hits + 1)
                    : Math.min(ground, Math.max(defenders + 1, ground - laterLandingNeed(planets, planet, opponent)));
            boolean bombarded = defenders > 0
                    && bombardment
                            .filter(found -> found.planet().equals(planet.getName()))
                            .isPresent();
            Invasion invasion = new Invasion(game, target, planet, seat, opponent, defenderGround, hand);
            double[] cannon = DiceOdds.distribution(cannonDice);
            if (hand.cancelsAny(cannon)) cannon = hand.cancellingCannon(cannon);
            PlanetOdds odds = planetOdds(invasion, fights, arrivals, sent, cannon, bombarded);
            wins.put(planet.getName(), odds.win());
            if (odds.win() <= 0 || odds.win() < rules.landMinWin()) continue;
            takeUnits(remaining, sent);
            arrivals.forEach(arrival -> takeUnits(arrival.ground(), sent));
            landings.put(planet.getName(), BoardView.groundForces(planet, seat) + sent);
            sentTo.put(planet.getName(), sent);
            fought |= defenders > 0;
            ourLoss += spaceWin * odds.ourLoss();
            theirLoss += spaceWin * odds.theirLoss() + spaceWin * odds.win() * PlanetStake.structures(planet, opponent);
            noKill *= 1 - spaceWin * odds.killChance();
        }
        return new Ground(landings, wins, sentTo, fought, ourLoss, theirLoss, noKill, dropped);
    }

    private static List<Planet> landingPlanets(Game game, Player seat, Strike strike) {
        Tile target = strike.target();
        StrikeRules rules = strike.rules();
        List<Planet> planets = new ArrayList<>();
        if (TacticalPlanner.isAnotherPlayersHome(game, seat, target) && !rules.homeLandings()) return planets;
        List<Planet> contested = TacticalPlanner.enemyPlanets(game, seat, target);
        contested.stream()
                .filter(planet -> TacticalPlanner.heldOnlyBy(game, seat, planet, strike.opponent()))
                .forEach(planets::add);
        planets.addAll(freePlanets(game, seat, target, rules, contested));
        return planets;
    }

    private static List<Arrival> arrivals(
            Player seat,
            Strike strike,
            Space fight,
            Map<UnitType, Integer> survivors,
            Map<UnitType, Integer> present,
            boolean landing) {
        Map<Map<UnitType, Integer>, Double> wonWith = fight.wonWith();
        double total =
                wonWith.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            wonWith = Map.of(survivors, 1.0);
            total = 1;
        }
        List<Arrival> arrivals = new ArrayList<>();
        for (Map.Entry<Map<UnitType, Integer>, Double> state : wonWith.entrySet()) {
            Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
            ground.putAll(strike.cargo());
            dropExcessCargo(seat, ground, state.getKey(), landing, nonFighterCount(present));
            strike.pulled().forEach((type, count) -> ground.merge(type, count, Integer::sum));
            arrivals.add(new Arrival(state.getValue() / total, merged(present, state.getKey()), ground));
        }
        return arrivals;
    }

    private static PlanetOdds planetOdds(
            Invasion invasion,
            Map<String, GroundFight> fights,
            List<Arrival> arrivals,
            int sent,
            double[] cannon,
            boolean bombarded) {
        Player seat = invasion.seat();
        int defenders = count(invasion.defenders());
        double win = 0;
        double ourLoss = 0;
        double theirLoss = 0;
        double killChance = 0;
        for (Arrival arrival : arrivals) {
            Map<UnitType, Integer> landing = new EnumMap<>(UnitType.class);
            landing.putAll(arrival.ground());
            trim(landing, sent);
            double[] shots = DiceOdds.capped(cannon, count(landing));
            double[] bombardment = bombarded
                    ? DiceOdds.pruned(
                            DiceOdds.capped(BombardmentRules.hitDistribution(seat, arrival.ships()), 2 * defenders))
                    : NO_HITS;
            for (int shot = 0; shot < shots.length; shot++) {
                if (shots[shot] == 0) continue;
                Map<UnitType, Integer> landed = new EnumMap<>(UnitType.class);
                landed.putAll(landing);
                double shotDownCost = shipCost(seat, removeLanders(landed, shot));
                for (int bombarding = 0; bombarding < bombardment.length; bombarding++) {
                    double chance = arrival.chance() * shots[shot] * bombardment[bombarding];
                    if (chance == 0) continue;
                    ourLoss += chance * shotDownCost;
                    if (count(landed) == 0) continue;
                    GroundFight fight = defenders == 0
                            ? new GroundFight(UNDEFENDED, 0)
                            : fight(invasion, fights, landed, bombarding);
                    Detailed detailed = fight.detailed();
                    win += chance * detailed.outcome().attackerWins();
                    ourLoss += chance * detailed.attackerCostLost();
                    theirLoss += chance * (detailed.defenderCostLost() + fight.bombardedCost());
                    killChance += chance * (fight.bombardedCost() > 0 ? 1 : detailed.defenderLosesUnit());
                }
            }
        }
        return new PlanetOdds(win, ourLoss, theirLoss, killChance);
    }

    private static GroundFight fight(
            Invasion invasion, Map<String, GroundFight> fights, Map<UnitType, Integer> landed, int bombardmentHits) {
        String key = invasion.planet().getName() + "|" + landed + "|" + bombardmentHits;
        return fights.computeIfAbsent(key, ignored -> groundFight(invasion, landed, bombardmentHits));
    }

    private static List<Planet> freePlanets(
            Game game, Player seat, Tile target, StrikeRules rules, List<Planet> alreadyListed) {
        return target.getPlanetUnitHolders().stream()
                .filter(planet -> !alreadyListed.contains(planet))
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                .filter(planet -> !BoardView.hasCustodians(planet) || rules.canPayCustodians())
                .sorted(IconStake.claimOrder(game, seat))
                .toList();
    }

    private static Map<UnitType, Integer> dropExcessCargo(
            Player seat,
            Map<UnitType, Integer> cargo,
            Map<UnitType, Integer> survivors,
            boolean landing,
            int residentNonFighters) {
        int capacity = 0;
        for (Map.Entry<UnitType, Integer> ship : survivors.entrySet()) {
            capacity += ship.getValue() * BoardView.capacity(seat, ship.getKey());
        }
        int fighters = survivors.getOrDefault(UnitType.Fighter, 0);
        boolean fightersFree = fightersMoveThemselves(seat);
        int room = landing || fightersFree ? capacity : Math.max(0, capacity - fighters);
        Map<UnitType, Integer> dropped = new EnumMap<>(UnitType.class);
        int excess = count(cargo) - room;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int removed = Math.min(Math.max(0, excess), cargo.getOrDefault(type, 0));
            if (removed <= 0) continue;
            cargo.merge(type, -removed, Integer::sum);
            dropped.put(type, removed);
            excess -= removed;
        }
        int excessFighters = fighters - Math.max(0, capacity - count(cargo));
        if (fightersFree) {
            int beyondFleet = excessFighters - fleetPoolFighters(seat, survivors, residentNonFighters);
            if (beyondFleet > 0) dropped.put(UnitType.Fighter, beyondFleet);
        } else if (landing && excessFighters > 0) {
            dropped.put(UnitType.Fighter, excessFighters);
        }
        return dropped;
    }

    private static int fleetPoolFighters(Player seat, Map<UnitType, Integer> survivors, int residentNonFighters) {
        int slots = FleetLimit.ships(seat) - nonFighterCount(survivors) - residentNonFighters;
        return Math.max(0, slots) * FighterWings.perFleetSlot(seat);
    }

    private static int nonFighterCount(Map<UnitType, Integer> ships) {
        return ships.entrySet().stream()
                .filter(ship -> ship.getKey() != UnitType.Fighter)
                .mapToInt(ship -> Math.max(0, ship.getValue()))
                .sum();
    }

    private static boolean fightersMoveThemselves(Player seat) {
        return BoardView.model(seat, UnitType.Fighter)
                .map(model -> model.getMoveValue() > 0)
                .orElse(false);
    }

    private static Map<UnitType, Integer> without(Map<UnitType, Integer> units, Map<UnitType, Integer> removed) {
        Map<UnitType, Integer> left = new EnumMap<>(UnitType.class);
        left.putAll(units);
        removed.forEach((type, count) -> left.computeIfPresent(type, (ignored, had) -> had - count));
        left.values().removeIf(count -> count <= 0);
        return left;
    }

    private static void trim(Map<UnitType, Integer> units, int keep) {
        int left = keep;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int kept = Math.min(left, units.getOrDefault(type, 0));
            units.put(type, kept);
            left -= kept;
        }
    }

    private static Map<UnitType, Integer> removeLanders(Map<UnitType, Integer> landing, int hits) {
        Map<UnitType, Integer> removed = new EnumMap<>(UnitType.class);
        int left = hits;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int taken = Math.min(left, landing.getOrDefault(type, 0));
            if (taken <= 0) continue;
            landing.merge(type, -taken, Integer::sum);
            removed.put(type, taken);
            left -= taken;
        }
        return removed;
    }

    static Map<UnitType, Integer> bombardingShips(Player seat, UnitHolder space, Side attacking) {
        Map<UnitType, Integer> bombarding = new EnumMap<>(UnitType.class);
        bombarding.putAll(BoardView.ships(space, seat));
        attacking.units().forEach((type, count) -> bombarding.merge(type, count, Integer::sum));
        return bombarding;
    }

    private static GroundFight groundFight(Invasion invasion, Map<UnitType, Integer> sent, int bombardmentHits) {
        Game game = invasion.game();
        Tile tile = invasion.tile();
        Planet planet = invasion.planet();
        Player seat = invasion.seat();
        Player opponent = invasion.opponent();
        int magenHits = MagenDefenseGrid.startOfCombatHits(opponent, planet);
        Side attacking = new Side(seat, sent);
        Side defending = new Side(opponent, invasion.defenders());
        Map<UnitType, Integer> defendersDamaged = BattleMath.damagedAmong(planet, opponent, defending.units());
        List<Combatant> defenders = CombatForces.combatants(game, tile, planet, defending, attacking, defendersDamaged);
        List<Combatant> bombarded = BattleMath.afterCannonFire(defenders, bombardmentHits);
        Force defence = CombatForces.force(opponent, bombarded, true);
        Force attack = CombatForces.withExpectedCopy(
                game,
                seat,
                opponent,
                CombatForces.force(
                        seat,
                        BattleMath.afterCannonFire(
                                CombatForces.combatants(game, tile, planet, attacking, defending, Map.of()), magenHits),
                        true),
                defence,
                true,
                true);
        Force firstAttack = firstRound(attack, invasion.hand().firstGroundRound(attack.units()));
        return new GroundFight(
                CombatOdds.resolveDetailed(attack, defence, firstAttack, defence, 0, 0),
                costOf(defenders) - costOf(bombarded));
    }

    private static Map<UnitType, Integer> groundOn(UnitHolder planet, Player player) {
        Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
        for (UnitType type : BoardView.GROUND_FORCES) {
            int count = BoardView.count(planet, player, type);
            if (count > 0) ground.put(type, count);
        }
        return ground;
    }

    private static double groundInSpaceCost(Player opponent, UnitHolder space) {
        Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
        for (UnitType type : BoardView.GROUND_FORCES) ground.put(type, BoardView.count(space, opponent, type));
        return shipCost(opponent, ground);
    }

    private static Map<UnitType, Integer> merged(Map<UnitType, Integer> first, Map<UnitType, Integer> second) {
        Map<UnitType, Integer> merged = new EnumMap<>(UnitType.class);
        merged.putAll(first);
        second.forEach((type, count) -> merged.merge(type, count, Integer::sum));
        merged.values().removeIf(count -> count <= 0);
        return merged;
    }

    private static int laterLandingNeed(List<Planet> planets, Planet planet, Player opponent) {
        int need = 0;
        for (Planet later : planets.subList(planets.indexOf(planet) + 1, planets.size())) {
            int defenders = count(groundOn(later, opponent));
            if (defenders > 0) need += defenders + 1;
        }
        return need;
    }

    private static int count(Map<UnitType, Integer> units) {
        return units.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void takeUnits(Map<UnitType, Integer> units, int wanted) {
        int left = wanted;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int taken = Math.min(left, units.getOrDefault(type, 0));
            if (taken > 0) units.merge(type, -taken, Integer::sum);
            left -= taken;
        }
    }

    private static double costOf(List<Combatant> combatants) {
        return combatants.stream().mapToDouble(Combatant::cost).sum();
    }

    private static double unitCost(Player player, UnitType type) {
        return BoardView.model(player, type).map(UnitModel::getCost).orElse(0f);
    }

    private static StrikeEstimate skippingSpaceCombat(Game game, Player seat, Strike strike, CombatHand hand) {
        Space skipped = new Space(1, 0, 0, 0, 0, 0, Map.of(strike.ships(), 1.0));
        Map<UnitType, Integer> present = BoardView.ships(BoardView.space(strike.target()), seat);
        Ground ground = ground(game, seat, strike, skipped, strike.ships(), present, hand, false);
        double shipsReturned = shipCost(seat, strike.ships()) + shipCost(seat, present);
        return new StrikeEstimate(
                1,
                false,
                false,
                ground.fought(),
                ground.landings(),
                ground.wins(),
                ground.sent(),
                shipsReturned + ground.ourLoss() + shipCost(seat, ground.dropped()),
                shipsReturned,
                ground.theirLoss(),
                0,
                1 - ground.noKill(),
                1,
                Map.of());
    }

    static StrikeEstimate afterSpaceWon(Game game, Player seat, Strike strike, double spaceWin) {
        Space won = new Space(spaceWin, 0, 0, 0, 0, 0, Map.of(strike.ships(), spaceWin));
        UnitHolder space = BoardView.space(strike.target());
        Ground ground = ground(
                game, seat, strike, won, strike.ships(), BoardView.ships(space, seat), CombatHand.of(game, seat), true);
        Map<String, Integer> landings = new LinkedHashMap<>();
        Map<String, Double> wins = new LinkedHashMap<>();
        Map<String, Integer> sent = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> landing : ground.landings().entrySet()) {
            if (!(strike.target().getUnitHolders().get(landing.getKey()) instanceof Planet planet)
                    || !heldBy(game, planet, strike.opponent())) {
                continue;
            }
            landings.put(landing.getKey(), landing.getValue());
            wins.put(landing.getKey(), ground.wins().get(landing.getKey()));
            sent.put(landing.getKey(), ground.sent().get(landing.getKey()));
        }
        return new StrikeEstimate(
                spaceWin,
                false,
                false,
                ground.fought(),
                landings,
                wins,
                sent,
                ground.ourLoss(),
                0,
                ground.theirLoss(),
                0,
                1 - ground.noKill(),
                0,
                strike.ships());
    }

    private static boolean heldBy(Game game, Planet planet, Player opponent) {
        return BoardView.controller(game, planet.getName()) == opponent || BoardView.groundForces(planet, opponent) > 0;
    }

    static Map<UnitType, Integer> cargoLeft(Player seat, Strike strike, StrikeEstimate estimate) {
        Map<UnitType, Integer> left = new EnumMap<>(UnitType.class);
        left.putAll(strike.cargo());
        dropExcessCargo(
                seat,
                left,
                estimate.survivors(),
                !estimate.landings().isEmpty(),
                BoardView.nonFighterShips(BoardView.space(strike.target()), seat));
        strike.pulled().forEach((type, count) -> left.merge(type, count, Integer::sum));
        takeUnits(
                left,
                estimate.groundSent().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum());
        left.values().removeIf(count -> count <= 0);
        return left;
    }

    static double shipCost(Player player, Map<UnitType, Integer> units) {
        double cost = 0;
        for (Map.Entry<UnitType, Integer> unit : units.entrySet()) {
            cost += unit.getValue() * unitCost(player, unit.getKey());
        }
        return cost;
    }
}
