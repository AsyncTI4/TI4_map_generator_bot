package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FactionAggression;
import ti4.ai.eval.FracturePrize;
import ti4.ai.eval.MecatolStake;
import ti4.ai.eval.ShardPrize;
import ti4.ai.eval.Territory;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.CopiedTechPolicy;
import ti4.ai.strategy.MecatolCash;
import ti4.ai.tactical.IncomingForces.Exclusion;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Units.UnitType;

final class OffenseOutlook {

    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final double ECONOMY_DENIAL_SHARE = 0.3;
    private static final double PROGRESS_DENIAL_SHARE = 0.5;
    private static final double KILL_SHARE = 0.25;
    private static final double BLOCKADE_VALUE = 1.0;
    private static final double HARM_CAP_BASE = 0.9;
    private static final double HARM_CAP_PER_THREAT = 2.0;
    private static final double ANGER_SHARE = 0.15;
    private static final double LOCKED_FLEET_SHARE = 0.5;
    private static final double RETALIATION_NORM = 1.2;
    private static final double LIMITED_REACH_RETALIATION = 0.5;
    private static final double NULLIFICATION_CHANCE = 0.8;
    private static final double NULLIFICATION_COST = 2.0;
    private static final double NEKRO_FEED_VALUE = 2.0;
    private static final double CAPTURE_VALUE = 1.0;
    private static final double EXTRA_ORIGIN_COST = 0.5;
    private static final double ONE_STEP_SHORT_BLOCK = 0.25;
    private static final double OWN_IMPERIAL_SHARE = 0.8;
    private static final double UNCONTROLLED_MECATOL_SHARE = 0.5;
    private static final int CUSTODIANS_INFLUENCE = 6;
    private static final String NULLIFICATION_FIELD = "nf";
    private static final String ABSOL_NULLIFICATION_FIELD = "absol_nf";
    private static final String SINGULARITY = "technological_singularity";
    private static final String VUIL_RAITH = "cabal";
    private static final Set<String> NOMADIC_ABILITIES = Set.of("nomadic", "mobile_command");
    private static final String NOMADIC_TECH = "tf-nomadic";

    private final Game game;
    private final Player seat;
    private final DefenseOutlook defense;
    private final Standings standings;
    private final Map<String, ObjectiveValue> publicViews = new HashMap<>();
    private final Map<String, Double> readyDenials = new HashMap<>();
    private final Map<String, Double> progressLosses = new HashMap<>();
    private final Map<String, Boolean> reachesGuarded = new HashMap<>();
    private final Map<String, Territory> territories = new HashMap<>();

    OffenseOutlook(Game game, Player seat, DefenseOutlook defense) {
        this.game = game;
        this.seat = seat;
        this.defense = defense;
        this.standings = defense.standings();
    }

    StrikeValue evaluate(Strike strike, StrikeEstimate estimate, ContextTerms terms, boolean full) {
        return evaluate(strike, estimate, terms, full, Double.NEGATIVE_INFINITY);
    }

    StrikeValue evaluate(Strike strike, StrikeEstimate estimate, ContextTerms terms, boolean full, double floor) {
        Player opponent = strike.opponent();
        Tile target = strike.target();
        double w = estimate.spaceWin();
        List<Planet> landed = landedPlanets(target, estimate);
        double meanGround = landed.isEmpty() ? 1 : meanGroundWin(landed, estimate);
        double ownObjective = w * terms.ownObjectiveGain() * meanGround;
        double planets = 0;
        double territory = 0;
        double mecatolHold = 0;
        double mecatolDenial = 0;
        double fractureDenial = 0;
        double heldPlanetValue = 0;
        for (Planet planet : landed) {
            double g = estimate.groundWins().get(planet.getName());
            double base = BoardView.planetValue(planet);
            double explore = terms.exploreValues().getOrDefault(planet.getName(), 0.0);
            double custodians = BoardView.hasCustodians(planet) ? VP : 0;
            planets += w
                    * g
                    * (base
                            + explore
                            + custodians
                            + neutralTempo(planet, opponent)
                            + FracturePrize.gain(game, seat, planet)
                            + ShardPrize.gain(game, seat, planet));
            territory += w * g * terms.territoryValues().getOrDefault(planet.getName(), 0.0);
            if (isMecatol(planet)) {
                mecatolHold += w * g * MecatolStake.ownHoldValue(game, seat);
                if (BoardView.controller(game, planet.getName()) == opponent) {
                    mecatolDenial += w * g * standings.denialWeight(opponent) * MecatolStake.holdValue(game, opponent);
                }
            }
            if (BoardView.controller(game, planet.getName()) == opponent) {
                heldPlanetValue += w * g * base;
                if (FracturePrize.isStyx(planet)) {
                    fractureDenial += w * g * standings.denialWeight(opponent) * FracturePrize.point(planet);
                }
                fractureDenial += w * g * standings.denialWeight(opponent) * ShardPrize.gain(game, seat, planet);
            }
        }
        double hurt = standings.hurtWeight(opponent);
        double economyDenial = hurt * ECONOMY_DENIAL_SHARE * heldPlanetValue;
        Defenders after = afterStrike(strike, estimate);
        List<Planet> planetsAfter = planetsAfter(target, landed);
        ownObjective -= retakenObjective(strike, estimate, after, planetsAfter, terms.exposedObjective() * meanGround);
        Denial denial = objectiveDenial(strike, estimate, landed, after, planetsAfter, terms);
        double kills = hurt * KILL_SHARE * estimate.theirExpectedLoss();
        double blockade = blockade(strike, estimate, landed, hurt);
        double nekroGain = nekroGain(strike, estimate);
        double defenseTerm = defenseRelief(strike, estimate, !full);
        double existing = existing(estimate, terms, landed);
        double harmCap = VP * (HARM_CAP_BASE + HARM_CAP_PER_THREAT * standings.winThreat(opponent));
        double harmTerms = heldPlanetValue
                + economyDenial
                + denial.objective()
                + denial.homeBlock()
                + kills
                + blockade
                + mecatolDenial
                + fractureDenial;
        double cappedHarm = Math.min(harmTerms, harmCap);
        double imperial = imperial(strike, estimate, landed, after, planetsAfter);
        double value = ownObjective
                + (planets - heldPlanetValue)
                + territory
                + mecatolHold
                + cappedHarm
                + imperial
                + nekroGain
                + defenseTerm
                + existing;
        double losses = estimate.ourExpectedLoss();
        double anger = 0;
        double counter = 0;
        double nekroFeed = 0;
        double capture = 0;
        double cancel = 0;
        double extraOrigins = EXTRA_ORIGIN_COST * Math.max(0, strike.origins().size() - 1);
        if (full && value - losses - extraOrigins >= floor) {
            double plastic = estimate.theirExpectedLoss() + heldPlanetValue;
            anger = anger(strike, estimate, plastic, denial.harmPoints(), ownObjective);
            counter = w * counterstrike(strike, estimate, after, planetsAfter, landed, terms.seatStake());
            nekroFeed = nekroFeed(opponent, estimate);
            capture = VUIL_RAITH.equals(opponent.getFaction()) ? CAPTURE_VALUE * estimate.ourLossChance() * hurt : 0;
            cancel = cancel(strike);
        }
        double score = (1 - cancel) * (value - losses - anger - counter - nekroFeed - capture)
                - cancel * NULLIFICATION_COST
                - extraOrigins;
        return new StrikeValue(
                ownObjective,
                planets,
                territory,
                economyDenial,
                denial.objective(),
                denial.homeBlock(),
                imperial,
                mecatolHold,
                mecatolDenial,
                fractureDenial,
                kills,
                blockade,
                nekroGain,
                defenseTerm,
                existing,
                value,
                losses,
                anger,
                counter,
                nekroFeed,
                capture,
                cancel,
                score);
    }

    private double neutralTempo(Planet planet, Player opponent) {
        if (!opponent.isNeutral()) return 0;
        Player controller = BoardView.controller(game, planet.getName());
        return controller == null || controller.isNeutral() ? TacticalPlanner.planetTempo(game) : 0;
    }

    private record Denial(double objective, double homeBlock, double harmPoints) {}

    boolean deniesObjectives(Player opponent, Planet planet) {
        return readyDenialOfPlanet(opponent, planet) > 0 || progressLossOfPlanet(opponent, planet) > 0;
    }

    double expansionImperial(Tile target, List<Planet> landed, Defenders ours) {
        Optional<Planet> mecatol = landed.stream()
                .filter(planet -> game.mecatols().contains(planet.getName()))
                .findFirst();
        if (mecatol.isEmpty()) return 0;
        List<Planet> held = new ArrayList<>(planetsAfter(target, landed));
        return imperialTerm(target, 1, defense, null, false, ours, held);
    }

    private double imperial(
            Strike strike, StrikeEstimate estimate, List<Planet> landed, Defenders after, List<Planet> planetsAfter) {
        Optional<Planet> mecatol = landed.stream()
                .filter(planet -> game.mecatols().contains(planet.getName()))
                .findFirst();
        if (mecatol.isEmpty()) return 0;
        double m = estimate.spaceWin() * estimate.groundWins().get(mecatol.get().getName());
        return imperialTerm(
                strike.target(),
                m,
                world(strike, estimate),
                strike.opponent(),
                estimate.escaped(),
                after,
                planetsAfter);
    }

    private double imperialTerm(
            Tile target,
            double m,
            DefenseOutlook world,
            Player struck,
            boolean escaped,
            Defenders after,
            List<Planet> planetsAfter) {
        int cashable = (standings.holdsImperial(seat) ? 1 : 0) + MecatolCash.overrulePoint(game, seat);
        double own = !seat.controlsMecatol(false) ? VP * OWN_IMPERIAL_SHARE * m * cashable : 0;
        double holder = standings
                .imperialHolder()
                .filter(found -> found != seat)
                .map(found -> holderImperial(found, target, m, world, struck, escaped, after, planetsAfter))
                .orElse(0.0);
        return own + holder;
    }

    private double holderImperial(
            Player holder,
            Tile target,
            double m,
            DefenseOutlook world,
            Player struck,
            boolean escaped,
            Defenders after,
            List<Planet> planetsAfter) {
        double before = holder.controlsMecatol(false) ? 1 : UNCONTROLLED_MECATOL_SHARE * reachable(holder, target);
        double takeVsUs = holder == struck && escaped
                ? 1
                : world.chancesAgainst(holder, target, Horizon.IMMEDIATE, after, planetsAfter)
                        .take();
        double standingShare = holder.controlsMecatol(false) ? 1 : UNCONTROLLED_MECATOL_SHARE;
        double afterTaking = m * standingShare * takeVsUs + (1 - m) * before;
        double lost = VP * standings.denialWeight(holder) * Math.max(0, before - afterTaking);
        double cap = VP * (HARM_CAP_BASE + HARM_CAP_PER_THREAT * standings.winThreat(holder));
        return Math.min(lost, cap);
    }

    private double reachable(Player holder, Tile mecatolTile) {
        IncomingForces.IncomingForce force = defense.force(holder, mecatolTile, Horizon.IMMEDIATE);
        if (force.isEmpty() || !force.carriesGround()) return 0;
        boolean custodians = mecatolTile.getPlanetUnitHolders().stream().anyMatch(BoardView::hasCustodians);
        return !custodians || Wallet.of(game, holder).influence() >= CUSTODIANS_INFLUENCE ? 1 : 0;
    }

    private Denial objectiveDenial(
            Strike strike,
            StrikeEstimate estimate,
            List<Planet> landed,
            Defenders after,
            List<Planet> planetsAfter,
            ContextTerms terms) {
        Player opponent = strike.opponent();
        if (opponent.isNeutral()) return new Denial(0, 0, 0);
        Tile target = strike.target();
        double w = estimate.spaceWin();
        double denialWeight = standings.denialWeight(opponent);
        double homeBlock = homeBlock(strike, estimate, landed, after, planetsAfter, denialWeight);
        double publicPoints = standings.publicPoints(opponent);
        double ready = 0;
        double progress = 0;
        for (Planet planet : landed) {
            if (BoardView.controller(game, planet.getName()) != opponent) continue;
            double g = estimate.groundWins().get(planet.getName());
            ready += g * readyDenialOfPlanet(opponent, planet);
            progress += g * progressLossOfPlanet(opponent, planet);
        }
        if (!BoardView.ships(BoardView.space(target), opponent).isEmpty() && !estimate.escaped()) {
            ready += readyDenialOfShips(opponent, target);
            progress += progressLossOfShips(opponent, target);
        }
        double readyPoints = Math.min(publicPoints, ready);
        double objective = homeBlock > 0 ? 0 : denialWeight * w * (VP * readyPoints + PROGRESS_DENIAL_SHARE * progress);
        double harmPoints = homeBlock > 0 ? homeBlockPoints(strike, estimate, landed) : VP * w * readyPoints;
        return new Denial(objective, homeBlock, harmPoints);
    }

    private double homeBlock(
            Strike strike,
            StrikeEstimate estimate,
            List<Planet> landed,
            Defenders after,
            List<Planet> planetsAfter,
            double denialWeight) {
        double maxGround = maxHomeGroundWin(strike, estimate, landed);
        if (maxGround <= 0) return 0;
        Player opponent = strike.opponent();
        double blocked = blockedPoints(opponent);
        if (blocked <= 0) return 0;
        double hold = opponent.isPassed() ? 1 : 1 - retake(strike, estimate, after, planetsAfter);
        return denialWeight * VP * blocked * hold * maxGround * estimate.spaceWin();
    }

    private double homeBlockPoints(Strike strike, StrikeEstimate estimate, List<Planet> landed) {
        return VP * blockedPoints(strike.opponent()) * estimate.spaceWin() * maxHomeGroundWin(strike, estimate, landed);
    }

    private double blockedPoints(Player opponent) {
        return standings.publicPoints(opponent) + (publicView(opponent).oneStepShort() ? ONE_STEP_SHORT_BLOCK : 0);
    }

    private double maxHomeGroundWin(Strike strike, StrikeEstimate estimate, List<Planet> landed) {
        Player opponent = strike.opponent();
        if (strike.target() != opponent.getHomeSystemTile() || isNomadic(opponent)) return 0;
        double max = 0;
        for (Planet planet : landed) {
            if (BoardView.controller(game, planet.getName()) != opponent) continue;
            max = Math.max(max, estimate.groundWins().get(planet.getName()));
        }
        return max;
    }

    private static boolean isNomadic(Player player) {
        return NOMADIC_ABILITIES.stream().anyMatch(player::hasAbility) || player.hasTech(NOMADIC_TECH);
    }

    private double retakenObjective(
            Strike strike, StrikeEstimate estimate, Defenders after, List<Planet> planetsAfter, double exposed) {
        if (exposed <= 0) return 0;
        return estimate.spaceWin() * exposed * retake(strike, estimate, after, planetsAfter);
    }

    private double retake(Strike strike, StrikeEstimate estimate, Defenders after, List<Planet> planetsAfter) {
        DefenseOutlook world = world(strike, estimate);
        return world.chancesAgainst(strike.opponent(), strike.target(), Horizon.IMMEDIATE, after, planetsAfter)
                .take();
    }

    private DefenseOutlook world(Strike strike, StrikeEstimate estimate) {
        if (estimate.escaped() || strike.opponent().isNeutral()) return defense;
        return defense.excluding(new Exclusion(strike.opponent(), strike.target()));
    }

    private double readyDenialOfPlanet(Player opponent, Planet planet) {
        String key = opponent.getFaction() + "#planet#" + planet.getName();
        Double known = readyDenials.get(key);
        if (known != null) return known;
        ObjectiveValue view = publicView(opponent);
        Set<String> broken = new HashSet<>(view.brokenReady(view.before().withoutPlanet(planet.getName())));
        broken.addAll(unpayableWithout(opponent, planet.getName()));
        double lost = readyPointsLost(opponent, broken);
        readyDenials.put(key, lost);
        return lost;
    }

    private double readyDenialOfShips(Player opponent, Tile target) {
        String key = opponent.getFaction() + "#ships#" + target.getPosition();
        Double known = readyDenials.get(key);
        if (known != null) return known;
        ObjectiveValue view = publicView(opponent);
        double lost = readyPointsLost(opponent, view.brokenReady(view.before().withoutShipsIn(target.getPosition())));
        readyDenials.put(key, lost);
        return lost;
    }

    private double progressLossOfPlanet(Player opponent, Planet planet) {
        String key = opponent.getFaction() + "#planet#" + planet.getName();
        return progressLosses.computeIfAbsent(key, ignored -> {
            ObjectiveValue view = publicView(opponent);
            return view.progressLoss(view.before().withoutPlanet(planet.getName()));
        });
    }

    private double progressLossOfShips(Player opponent, Tile target) {
        String key = opponent.getFaction() + "#ships#" + target.getPosition();
        return progressLosses.computeIfAbsent(key, ignored -> {
            ObjectiveValue view = publicView(opponent);
            return view.progressLoss(view.before().withoutShipsIn(target.getPosition()));
        });
    }

    private double readyPointsLost(Player opponent, Set<String> broken) {
        List<String> remaining = standings.ready(opponent).stream()
                .filter(id -> !broken.contains(id))
                .toList();
        return standings.publicPoints(opponent) - standings.publicPoints(opponent, remaining);
    }

    private Set<String> unpayableWithout(Player opponent, String planet) {
        Wallet wallet = Wallet.of(game, opponent);
        Wallet without = wallet.without(new Wallet.Payment(List.of(planet), List.of(), 0, 0));
        Set<String> unpayable = new HashSet<>();
        for (String id : standings.ready(opponent)) {
            Optional<SpendCost> cost = ObjectiveCatalog.spendCost(id);
            if (cost.isPresent() && wallet.canPay(cost.get()) && !without.canPay(cost.get())) unpayable.add(id);
        }
        return unpayable;
    }

    private double blockade(Strike strike, StrikeEstimate estimate, List<Planet> landed, double hurt) {
        Player opponent = strike.opponent();
        Tile target = strike.target();
        if (estimate.survivors().isEmpty()) return 0;
        boolean keepsDock = target.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.count(planet, opponent, UnitType.Spacedock) > 0)
                .anyMatch(planet -> !landed.contains(planet));
        return keepsDock ? hurt * BLOCKADE_VALUE * estimate.spaceWin() : 0;
    }

    private double nekroGain(Strike strike, StrikeEstimate estimate) {
        if (!seat.hasAbility(SINGULARITY) || strike.opponent().isNeutral()) return 0;
        List<Double> values = new ArrayList<>(copyableValues(seat, strike.opponent()));
        values.sort(java.util.Comparator.reverseOrder());
        double best = values.isEmpty() ? 0 : values.get(0);
        double second = values.size() < 2 ? 0 : values.get(1);
        if (estimate.spaceCombat()) return best * estimate.spaceKillChance() + second * estimate.groundKillChance();
        return best * estimate.groundKillChance();
    }

    private List<Double> copyableValues(Player taker, Player owner) {
        return ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(taker, owner, new ArrayList<>(), game)
                .stream()
                .map(alias -> CopiedTechPolicy.value(game, taker, alias))
                .toList();
    }

    private double nekroFeed(Player opponent, StrikeEstimate estimate) {
        if (!opponent.hasAbility(SINGULARITY)) return 0;
        if (ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(opponent, seat, new ArrayList<>(), game)
                .isEmpty()) {
            return 0;
        }
        return NEKRO_FEED_VALUE * estimate.ourLossChance() * standings.hurtWeight(opponent);
    }

    private double cancel(Strike strike) {
        Player opponent = strike.opponent();
        boolean hasShips =
                !BoardView.ships(BoardView.space(strike.target()), opponent).isEmpty();
        boolean field = opponent.hasTechReady(NULLIFICATION_FIELD) || opponent.hasTechReady(ABSOL_NULLIFICATION_FIELD);
        return hasShips && field && opponent.getStrategicCC() > 0 ? NULLIFICATION_CHANCE : 0;
    }

    private double existing(StrikeEstimate estimate, ContextTerms terms, List<Planet> landed) {
        double w = estimate.spaceWin();
        double meanGround = landed.isEmpty() ? 0 : meanGroundWin(landed, estimate);
        double total = w * (terms.spaceSecretBonus() + terms.homeDefence() + terms.makeAnExample());
        total += w * meanGround * terms.groundSecretBonus();
        total += terms.retakeValue()
                * w
                * landed.stream()
                        .mapToDouble(planet -> estimate.groundWins().get(planet.getName()))
                        .sum();
        return total;
    }

    private double anger(
            Strike strike, StrikeEstimate estimate, double plastic, double scoringDenial, double ownObjective) {
        Player opponent = strike.opponent();
        if (opponent.isNeutral()) return 0;
        double grievance = plastic * angerSensitivity(strike.target(), opponent)
                + scoringDenial * Grievance.scoringDenial(standings.leadOverField(opponent));
        double relief = Grievance.relief(standings.winThreat(opponent), standings.winThreat(seat), ownObjective / VP);
        return ANGER_SHARE * grievance * retaliation(strike, estimate) * relief;
    }

    private double retaliation(Strike strike, StrikeEstimate estimate) {
        Player opponent = strike.opponent();
        double free = retaliatingFleet(opponent, strike.target(), estimate);
        double ratio = Math.clamp(free / Math.max(1, defense.fleetCostTotal(seat)), 0.0, 1.0);
        double reach = threatensGuardedSystems(opponent) ? 1 : LIMITED_REACH_RETALIATION;
        return ratio * FactionAggression.of(opponent).factor() / RETALIATION_NORM * reach;
    }

    private double retaliatingFleet(Player opponent, Tile target, StrikeEstimate estimate) {
        boolean idle = opponent.isPassed() || opponent.getTacticalCC() <= 0;
        double kept = estimate.escaped() ? 1 : 1 - estimate.spaceWin();
        double total = 0;
        for (Tile tile : game.getTileMap().values()) {
            double cost = shipCost(opponent, tile);
            if (cost <= 0) continue;
            double survives = tile == target ? kept : 1;
            double unlocked = idle || tile.hasPlayerCC(opponent) ? LOCKED_FLEET_SHARE : 1;
            total += cost * survives * unlocked;
        }
        return total;
    }

    private static double shipCost(Player player, Tile tile) {
        double cost = 0;
        for (Map.Entry<UnitType, Integer> ship :
                BoardView.ships(BoardView.space(tile), player).entrySet()) {
            if (ship.getKey() != UnitType.Fighter) cost += ship.getValue() * IncomingForces.cost(player, ship.getKey());
        }
        return cost;
    }

    private double angerSensitivity(Tile target, Player opponent) {
        Territory territory =
                territories.computeIfAbsent(opponent.getFaction(), faction -> Territory.of(game, opponent));
        return Grievance.location(territory.zone(target));
    }

    private boolean isMecatol(Planet planet) {
        return game.mecatols().contains(planet.getName());
    }

    private boolean threatensGuardedSystems(Player opponent) {
        return reachesGuarded.computeIfAbsent(
                opponent.getFaction(),
                faction -> defense.guardedTiles().stream()
                        .anyMatch(tile -> !defense.force(opponent, tile, Horizon.NEXT_ROUND)
                                .isEmpty()));
    }

    private double counterstrike(
            Strike strike,
            StrikeEstimate estimate,
            Defenders after,
            List<Planet> planetsAfter,
            List<Planet> landed,
            double seatStake) {
        double shipStake = 0.5 * after.shipCost(seat);
        double planetStake = landed.stream()
                .mapToDouble(planet -> BoardView.planetValue(planet)
                        + FracturePrize.point(planet)
                        + (isMecatol(planet) ? MecatolStake.ownHoldValue(game, seat) : 0))
                .sum();
        return world(strike, estimate)
                .counterstrike(
                        strike.target(), after, planetsAfter, shipStake, planetStake, seatStake, strike.opponent());
    }

    private double defenseRelief(Strike strike, StrikeEstimate estimate, boolean originsOnly) {
        DefenseOutlook world = world(strike, estimate);
        double w = estimate.spaceWin();
        double total = 0;
        for (Tile guarded : defense.guardedTiles()) {
            if (guarded == strike.target()) continue;
            boolean origin = strike.origins().contains(guarded);
            if (originsOnly && !origin) continue;
            Defenders now = defense.defendersAt(guarded);
            Defenders left = defense.defendersAfter(guarded, strike.moves());
            double term = defense.cost(guarded, now)
                    - (w * world.cost(guarded, left) + (1 - w) * defense.cost(guarded, left));
            total += origin ? term : Math.max(0, term);
        }
        return total;
    }

    Defenders afterStrike(Strike strike, StrikeEstimate estimate) {
        Defenders after = defense.defendersAt(strike.target());
        for (Map.Entry<UnitType, Integer> ship : estimate.survivors().entrySet()) {
            after = after.plusShips(ship.getKey(), ship.getValue(), 0);
        }
        Map<UnitType, Integer> cargo = new java.util.EnumMap<>(UnitType.class);
        cargo.putAll(strike.cargo());
        strike.pulled().forEach((type, count) -> cargo.merge(type, count, Integer::sum));
        for (Map.Entry<String, Integer> sent : estimate.groundSent().entrySet()) {
            int left = sent.getValue();
            for (UnitType type : BoardView.GROUND_FORCES) {
                int taken = Math.min(left, cargo.getOrDefault(type, 0));
                if (taken <= 0) continue;
                after = after.plusGround(sent.getKey(), type, taken);
                cargo.merge(type, -taken, Integer::sum);
                left -= taken;
            }
        }
        return after;
    }

    private List<Planet> landedPlanets(Tile target, StrikeEstimate estimate) {
        List<Planet> landed = new ArrayList<>();
        for (String name : estimate.landings().keySet()) {
            if (target.getUnitHolders().get(name) instanceof Planet planet) landed.add(planet);
        }
        return landed;
    }

    private List<Planet> planetsAfter(Tile target, List<Planet> landed) {
        List<Planet> held = new ArrayList<>(landed);
        target.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getPlanets().contains(planet.getName()) && !held.contains(planet))
                .forEach(held::add);
        return held;
    }

    private static double meanGroundWin(List<Planet> landed, StrikeEstimate estimate) {
        return landed.stream()
                .mapToDouble(planet -> estimate.groundWins().get(planet.getName()))
                .average()
                .orElse(1);
    }

    private ObjectiveValue publicView(Player player) {
        return publicViews.computeIfAbsent(player.getFaction(), faction -> ObjectiveValue.publicOnly(game, player));
    }
}
