package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.FactionAggression;
import ti4.ai.eval.FracturePrize;
import ti4.ai.eval.MecatolStake;
import ti4.ai.eval.PlanetStake;
import ti4.ai.letnev.L4Disruptors;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.IncomingForces.Exclusion;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public final class DefenseOutlook {

    static final double GARRISON_TOLERANCE = 0.5;
    static final double HOLD_MIN_GAIN = 0.3;
    private static final double REACH_FLEET_COST = 10.0;
    private static final double POSTURE_BASE = 0.1;
    private static final double POSTURE_PER_ROUND = 0.2;
    private static final int POSTURE_ROUNDS = 4;
    private static final double POSTURE_PER_POINT_OF_LEAD = 0.1;
    private static final int POSTURE_MAX_LEAD = 3;
    private static final int POSTURE_MAX_DEFICIT = 2;
    private static final double POSTURE_NEAR_WIN = 0.2;
    private static final double POSTURE_SPACE_FLOOR = 0.2;
    private static final double MULTI_PLANET_GROUND_LEVEL = 0.6;
    private static final double TWO_MOVE_WEIGHT = 0.5;
    private static final double MOTIVE_FLOOR = 0.4;
    private static final double MIN_STANDING_LEVEL = 0.2;
    static final double PUBLIC_SCORING_LOCK = 8.0;
    private static final double NEAR_WIN_SCORING_LOCK = 12.0;
    static final double SCORING_CRITICAL = 4.0;
    private static final double CONTESTED_GAIN = 4.0;
    private static final double MIN_FEAR = 0.05;
    private static final double HOME_PRIZE = 3.0;
    private static final double ACTION_COST = 2.0;
    private static final double THUNDERS_EDGE_ACTION_COST = 0.5;
    private static final double APPETITE_SCALE = 3.0;
    private static final double APPETITE_MARGIN = 0.5;
    private static final double SOFT_POSITIVE_LINEAR = 30;
    private static final double NEXT_ROUND_APPETITE_FLOOR = 0.5;
    private static final double SCORING_REASON_SCALE = 8.0;
    private static final double DENIAL_PER_REASON = 4.0;
    private static final double SPACE_STAKE_SHARE = 0.5;
    private static final double SCORING_LOSS_SHARE = 0.5;
    private static final double NEXT_ROUND_WEIGHT = 0.5;
    private static final double TECH_PER_STEP = 0.03;
    private static final int TECH_STEPS = 6;
    private static final double NAVY_WEIGHT = 0.2;
    private static final double MIN_COMMITMENT = 0.85;
    private static final double SPARE_FLEET_FOR_FULL_COMMITMENT = 0.5;
    private static final double CEASEFIRE_FEAR = 0.3;
    private static final double STRUCK_PLAYER_FEAR = 1.2;
    private static final double UNIT_COST_FLOOR = 0.5;
    private static final double RISK_TIE = 1e-9;
    private static final int NEAR_WIN_GAP = 3;
    private static final int BUNDLE_SIZES = 3;
    private static final String CEASEFIRE = "_cf";
    private static final String GRAVITY_DRIVE = "gd";

    record Chances(double space, double take, double groundMax) {}

    private record SpaceFight(Force attack, Force defence) {}

    record Blend(DefenseOutlook excluded, double w0) {}

    private record Bundle(double gain, double score, int capacity, double cost, List<UnitMove> remaining) {}

    private final Game game;
    private final Player seat;
    private final IncomingForces incoming;
    private final DefenseOutlook root;
    private final Exclusion exclusion;
    private final Map<String, DefenseOutlook> exclusions = new HashMap<>();
    private List<Player> enemies;
    private Tile home;
    private boolean homeKnown;
    private ObjectiveValue own;
    private Standings standings;
    private List<Tile> guardedTiles;
    private final Map<String, ObjectiveValue> publicViews;
    private final Map<String, Boolean> guarded;
    private final Map<String, Double> scoringLosses;
    private final Map<String, Double> stakes;
    private final Map<String, Double> ownShipLosses;
    private final Map<String, Double> publicGains;
    private final Map<String, Double> spaceGains;
    private final Map<String, Map<String, Double>> alternatives;
    private final Map<String, Double> spaceChances;
    private Double homeDefenseLevel;
    private Planet anchor;
    private GroundCampaign campaign;
    private boolean anchorKnown;
    private final Map<String, Double> groundChances;
    private final Map<String, Double> fearCeilings = new HashMap<>();
    private final Map<String, Double> fleetCosts = new HashMap<>();
    private final Map<String, Double> costs = new HashMap<>();
    private final Map<String, Double> threats = new HashMap<>();
    private final Map<String, Double> postures = new HashMap<>();

    public DefenseOutlook(Game game, Player seat) {
        this(game, seat, new IncomingForces(game), null, null);
    }

    private DefenseOutlook(
            Game game,
            Player seat,
            IncomingForces incoming,
            @Nullable DefenseOutlook root,
            @Nullable Exclusion exclusion) {
        this.game = game;
        this.seat = seat;
        this.incoming = incoming;
        this.root = root;
        this.exclusion = exclusion;
        this.publicViews = root == null ? new HashMap<>() : root.publicViews;
        this.guarded = root == null ? new HashMap<>() : root.guarded;
        this.scoringLosses = root == null ? new HashMap<>() : root.scoringLosses;
        this.stakes = root == null ? new HashMap<>() : root.stakes;
        this.ownShipLosses = root == null ? new HashMap<>() : root.ownShipLosses;
        this.publicGains = root == null ? new HashMap<>() : root.publicGains;
        this.spaceGains = root == null ? new HashMap<>() : root.spaceGains;
        this.alternatives = root == null ? new HashMap<>() : root.alternatives;
        this.spaceChances = root == null ? new HashMap<>() : root.spaceChances;
        this.groundChances = root == null ? new HashMap<>() : root.groundChances;
    }

    GroundCampaign campaign() {
        if (root != null) return root.campaign();
        if (campaign == null) campaign = GroundCampaign.of(game, seat);
        return campaign;
    }

    DefenseOutlook excluding(Exclusion removed) {
        DefenseOutlook base = root == null ? this : root;
        String key = removed.owner().getFaction() + "#" + removed.tile().getPosition();
        return base.exclusions.computeIfAbsent(
                key,
                ignored -> new DefenseOutlook(
                        game, seat, new IncomingForces(game, removed, base.incoming), base, removed));
    }

    public static double homeCost(Game game, Player seat, boolean immediateOnly) {
        DefenseOutlook outlook = new DefenseOutlook(game, seat);
        Tile home = outlook.home();
        return home == null ? 0 : outlook.cost(home, outlook.defendersAt(home), immediateOnly);
    }

    public double lockCost(Tile tile) {
        return cost(tile, defendersAt(tile), false);
    }

    Standings standings() {
        if (root != null) return root.standings();
        if (standings == null) standings = new Standings(game, seat, incoming);
        return standings;
    }

    Defenders defendersAt(Tile tile) {
        return Defenders.of(seat, tile);
    }

    Defenders defendersAfter(Tile tile, List<UnitMove> moves) {
        return defendersAt(tile).minus(movesFrom(tile, moves));
    }

    List<Tile> guardedTiles() {
        if (root != null) return root.guardedTiles();
        if (guardedTiles == null) {
            guardedTiles = game.getTileMap().values().stream()
                    .filter(tile -> !seatPlanets(tile).isEmpty() || tile.containsPlayersUnits(seat))
                    .filter(this::isGuarded)
                    .toList();
        }
        return guardedTiles;
    }

    double cost(Tile tile) {
        return cost(tile, defendersAt(tile), false);
    }

    double cost(Tile tile, Defenders defenders) {
        return cost(tile, defenders, false);
    }

    boolean isGuarded(Tile tile) {
        if (root != null) return root.isGuarded(tile);
        String key = tile.getPosition();
        Boolean known = guarded.get(key);
        if (known != null) return known;
        boolean result = computeGuarded(tile);
        guarded.put(key, result);
        return result;
    }

    boolean isHome(Tile tile) {
        return tile == home();
    }

    double publicScoringLock() {
        return nearWin() ? NEAR_WIN_SCORING_LOCK : PUBLIC_SCORING_LOCK;
    }

    IncomingForce force(Player enemy, Tile tile, Horizon horizon) {
        return incoming.of(enemy, tile, horizon);
    }

    IncomingForce activatingForce(Player enemy, Tile tile) {
        return incoming.activating(enemy, tile);
    }

    double leaveCost(Tile origin, List<UnitMove> moves) {
        return leaveCost(origin, moves, null);
    }

    double leaveCost(Tile origin, List<UnitMove> moves, @Nullable Blend blend) {
        if (!isGuarded(origin)) return 0;
        Defenders current = defendersAt(origin);
        Defenders after = current.minus(movesFrom(origin, moves));
        return Math.max(0, blendedCost(origin, after, blend) - blendedCost(origin, current, blend));
    }

    List<UnitMove> holdBack(Tile origin, List<UnitMove> leavingRegardless, List<UnitMove> candidates) {
        return holdBack(origin, leavingRegardless, candidates, null);
    }

    List<UnitMove> holdBack(
            Tile origin, List<UnitMove> leavingRegardless, List<UnitMove> candidates, @Nullable Blend blend) {
        if (candidates.isEmpty() || !isGuarded(origin)) return candidates;
        Defenders current = defendersAt(origin);
        Defenders afterRegardless = current.minus(movesFrom(origin, leavingRegardless));
        double base = blendedCost(origin, current, blend);
        List<UnitMove> leaving = new ArrayList<>(candidates);
        while (!leaving.isEmpty()) {
            double now = blendedCost(origin, afterRegardless.minus(movesFrom(origin, leaving)), blend);
            if (now - base <= GARRISON_TOLERANCE) break;
            Bundle best = bestKeptBundle(origin, afterRegardless, leaving, now, blend);
            if (best == null || best.gain() < HOLD_MIN_GAIN) break;
            leaving = new ArrayList<>(best.remaining());
        }
        return leaving;
    }

    double homeDefenseLevel() {
        if (homeDefenseLevel == null) homeDefenseLevel = computeHomeDefenseLevel();
        return homeDefenseLevel;
    }

    private double computeHomeDefenseLevel() {
        Tile tile = home();
        if (tile == null) return 0;
        return enemyReach(tile) * homePressure();
    }

    private double enemyReach(Tile tile) {
        double unthreatened = 1;
        for (Player enemy : enemies()) {
            IncomingForce near = incoming.of(enemy, tile, Horizon.NEXT_ROUND);
            IncomingForce approaching = incoming.approaching(enemy, tile);
            double fleetCost =
                    near.fleetCost() + TWO_MOVE_WEIGHT * Math.max(0, approaching.fleetCost() - near.fleetCost());
            if (fleetCost <= 0) continue;
            double strength = clamp(fleetCost / REACH_FLEET_COST, 0, 1);
            unthreatened *= 1 - motive(enemy, tile, near.isEmpty() ? approaching : near) * strength;
        }
        return 1 - unthreatened;
    }

    private double motive(Player enemy, Tile tile, IncomingForce force) {
        Defenders current = defendersAt(tile);
        Defenders emptied = new Defenders(Defenders.Stack.EMPTY, current.groundInSpace(), current.planets());
        double gain = appetite(enemy, tile, force, emptied, chances(tile, force, emptied)) + actionCost(enemy);
        return Math.max(MOTIVE_FLOOR, attackLikelihood(enemy, tile, Horizon.NEXT_ROUND, gain));
    }

    private double homePressure() {
        int roundsPlayed = Math.min(POSTURE_ROUNDS, Math.max(0, game.getRound() - 1));
        int lead = Math.clamp(pointLead(), -POSTURE_MAX_DEFICIT, POSTURE_MAX_LEAD);
        double pressure = POSTURE_BASE
                + POSTURE_PER_ROUND * roundsPlayed
                + POSTURE_PER_POINT_OF_LEAD * lead
                + (nearWin() ? POSTURE_NEAR_WIN : 0);
        return clamp(pressure, 0, 1);
    }

    private int pointLead() {
        int best =
                enemies().stream().mapToInt(Player::getTotalVictoryPoints).max().orElse(0);
        return seat.getTotalVictoryPoints() - best;
    }

    private double posture(Tile tile, Defenders defenders) {
        double level = homeDefenseLevel();
        if (level < MIN_STANDING_LEVEL) return 0;
        double tolerated = 1 - level * (1 - POSTURE_SPACE_FLOOR);
        double groundShare = groundShare(tile, level);
        Planet held = groundShare > 0 ? anchorPlanet() : null;
        double worst = 0;
        for (Player enemy : enemies()) {
            IncomingForce near = incoming.of(enemy, tile, Horizon.NEXT_ROUND);
            IncomingForce approaching = incoming.approaching(enemy, tile);
            worst = Math.max(worst, exposure(tile, near, defenders, tolerated, groundShare, held));
            worst = Math.max(
                    worst, TWO_MOVE_WEIGHT * exposure(tile, approaching, defenders, tolerated, groundShare, held));
        }
        return level * stake(tile) * worst;
    }

    private double exposure(
            Tile tile,
            IncomingForce force,
            Defenders defenders,
            double tolerated,
            double groundShare,
            @Nullable Planet held) {
        if (force.isEmpty()) return 0;
        double space = spaceChance(tile, force, defenders);
        double exposed = Math.max(0, space - tolerated);
        if (held != null && force.carriesGround()) {
            double take = space * groundChance(tile, force, held, defenders);
            exposed += groundShare * Math.max(0, take - tolerated);
        }
        return exposed;
    }

    private double groundShare(Tile tile, double level) {
        if (seatPlanets(tile).size() <= 1) return 1;
        return clamp((level - MULTI_PLANET_GROUND_LEVEL) / (1 - MULTI_PLANET_GROUND_LEVEL), 0, 1);
    }

    @Nullable
    Planet anchorPlanet() {
        if (!anchorKnown) {
            Tile tile = home();
            anchor = tile == null
                    ? null
                    : seatPlanets(tile).stream()
                            .max(Comparator.comparingInt(
                                            (Planet planet) -> BoardView.count(planet, seat, UnitType.Spacedock))
                                    .thenComparingDouble(BoardView::planetValue)
                                    .thenComparing(Planet::getName, Comparator.reverseOrder()))
                            .orElse(null);
            anchorKnown = true;
        }
        return anchor;
    }

    int groundToKeep(Tile home, Planet planet, List<UnitMove> shipsLeaving) {
        return groundToKeep(home, planet, shipsLeaving, null);
    }

    int groundToKeep(Tile home, Planet planet, List<UnitMove> shipsLeaving, @Nullable Blend blend) {
        Defenders current = defendersAt(home);
        int present = current.groundOn(planet.getName());
        if (present == 0 || !isGuarded(home)) return 0;
        Defenders baseline = current.minus(movesFrom(home, shipsLeaving));
        double base = blendedCost(home, current, blend);
        for (int keep = 0; keep < present; keep++) {
            if (blendedCost(home, baseline.withGround(planet.getName(), keep), blend) - base <= GARRISON_TOLERANCE) {
                return keep;
            }
        }
        return present;
    }

    private double blendedCost(Tile tile, Defenders defenders, @Nullable Blend blend) {
        double standing = departureCost(tile, defenders);
        if (blend == null) return standing;
        return blend.w0() * blend.excluded().departureCost(tile, defenders) + (1 - blend.w0()) * standing;
    }

    private double departureCost(Tile tile, Defenders defenders) {
        double threat = cost(tile, defenders);
        if (!isHome(tile)) return threat;
        return Math.max(threat, cached(postures, defenders.key(), () -> posture(tile, defenders)));
    }

    double fear(Player enemy, Tile tile, Horizon horizon, Defenders defenders) {
        IncomingForce force = incoming.of(enemy, tile, horizon);
        if (force.isEmpty() || fearCeiling(enemy, tile, horizon, force) < MIN_FEAR) return 0;
        return fear(enemy, tile, horizon, force, defenders, chances(tile, force, defenders));
    }

    double target(double fear) {
        double calm = 1 - fear;
        return 0.5 + 0.5 * calm * calm;
    }

    Chances chances(Player enemy, Tile tile, Horizon horizon, Defenders defenders) {
        IncomingForce force = incoming.of(enemy, tile, horizon);
        return force.isEmpty() ? new Chances(0, 0, 0) : chances(tile, force, defenders);
    }

    Chances chancesAgainst(Player enemy, Tile tile, Horizon horizon, Defenders after, List<Planet> planets) {
        IncomingForce force = incoming.of(enemy, tile, horizon);
        if (force.isEmpty()) return new Chances(0, 0, 0);
        double space = spaceChance(tile, force, after);
        if (!force.carriesGround() || planets.isEmpty()) return new Chances(space, 0, 0);
        double groundMax = 0;
        for (Planet planet : planets) groundMax = Math.max(groundMax, groundChance(tile, force, planet, after));
        return new Chances(space, space * groundMax, groundMax);
    }

    // TODO: the struck player's PDS on planets the strike takes still count against the staying fleet
    double counterstrike(
            Tile tile,
            Defenders after,
            List<Planet> planetsAfter,
            double shipStake,
            double planetStake,
            Player struck) {
        return counterstrike(tile, after, planetsAfter, shipStake, planetStake, 0, struck);
    }

    double counterstrike(
            Tile tile,
            Defenders after,
            List<Planet> planetsAfter,
            double shipStake,
            double planetStake,
            double immediateStake,
            Player struck) {
        if (isGuarded(tile)) return 0;
        double worst = 0;
        for (Player enemy : enemies()) {
            for (Horizon horizon : Horizon.values()) {
                IncomingForce force = incoming.of(enemy, tile, horizon);
                if (force.isEmpty()) continue;
                double eagerness = enemy == struck ? STRUCK_PLAYER_FEAR : 1.0;
                double fear = Math.min(1, fearCeiling(enemy, tile, horizon, force, planetsAfter) * eagerness);
                if (fear <= 0) continue;
                Chances chances = chancesAgainst(enemy, tile, horizon, after, planetsAfter);
                double seatStake = horizon == Horizon.IMMEDIATE ? immediateStake : 0;
                double exposed = chances.space() * shipStake + chances.take() * (planetStake + seatStake);
                worst = Math.max(worst, weight(enemy, horizon) * fear * exposed);
            }
        }
        return worst;
    }

    @Nullable
    Planet mostExposedPlanet(Tile tile, Defenders defenders) {
        Planet exposed = null;
        double worst = -1;
        for (Planet planet : seatPlanets(tile)) {
            double risk = groundRisk(tile, planet, defenders);
            boolean riskier = risk > worst + RISK_TIE;
            boolean tiedAndWorthMore = Math.abs(risk - worst) <= RISK_TIE
                    && exposed != null
                    && BoardView.planetValue(planet) > BoardView.planetValue(exposed);
            if (riskier || tiedAndWorthMore) {
                exposed = planet;
                worst = risk;
            }
        }
        return exposed;
    }

    private double groundRisk(Tile tile, Planet planet, Defenders defenders) {
        double risk = 0;
        for (Player enemy : enemies()) {
            for (Horizon horizon : Horizon.values()) {
                IncomingForce force = incoming.of(enemy, tile, horizon);
                if (!force.isEmpty() && force.carriesGround()) {
                    risk = Math.max(risk, groundChance(tile, force, planet, defenders));
                }
            }
        }
        return risk;
    }

    double weight(Player enemy, Horizon horizon) {
        if (horizon == Horizon.IMMEDIATE) return 1.0;
        boolean pokWarfare = IncomingForces.holdsUnplayedPokWarfare(game, enemy);
        boolean noAnotherTurn = seat.isPassed() || seat.getTacticalCC() < 2;
        return pokWarfare || noAnotherTurn ? 1.0 : NEXT_ROUND_WEIGHT;
    }

    double stake(Tile tile) {
        return cached(stakes, tile.getPosition(), () -> {
            double planets = 0;
            for (Planet planet : seatPlanets(tile)) {
                planets = Math.max(
                        planets, PlanetStake.of(game, seat, planet, own()) + PlanetStake.structures(planet, seat));
            }
            return planets + (isHome(tile) ? publicScoringLock() : 0) + mecatolHandover(tile) + mecatolHold(tile);
        });
    }

    private double mecatolHold(Tile tile) {
        return controlsMecatol(tile) ? MecatolStake.holdValue(game, seat) : 0;
    }

    private double mecatolHandover(Tile tile) {
        if (!controlsMecatol(tile)) return 0;
        return standings()
                .imperialHolder()
                .filter(holder -> holder != seat)
                .map(holder -> standings().denialWeight(holder) * ObjectiveValue.VICTORY_POINT_VALUE)
                .orElse(0.0);
    }

    double scoringLoss(Tile tile) {
        return cached(scoringLosses, tile.getPosition(), () -> {
            ObjectiveValue objectives = own();
            double loss = shipLoss(tile);
            for (Planet planet : seatPlanets(tile)) {
                double progress =
                        Math.max(0, -objectives.gain(objectives.before().withoutPlanet(planet.getName())));
                loss += Math.max(progress, TraitRaid.holdLoss(game, seat, planet));
            }
            return loss;
        });
    }

    double shipLoss(Tile tile) {
        return cached(ownShipLosses, tile.getPosition(), () -> {
            ObjectiveValue objectives = own();
            return Math.max(0, -objectives.gain(objectives.before().withoutShipsIn(tile.getPosition())));
        });
    }

    private boolean computeGuarded(Tile tile) {
        return isHome(tile)
                || hasSeatDock(tile)
                || controlsMecatol(tile)
                || FracturePrize.holdsStyx(tile, seat)
                || scoringLoss(tile) >= SCORING_CRITICAL
                || contested(tile);
    }

    private boolean hasSeatDock(Tile tile) {
        return seatPlanets(tile).stream().anyMatch(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0);
    }

    private boolean controlsMecatol(Tile tile) {
        return seatPlanets(tile).stream().anyMatch(planet -> game.mecatols().contains(planet.getName()));
    }

    private boolean contested(Tile tile) {
        if (seatPlanets(tile).isEmpty()) return false;
        for (Player enemy : enemies()) {
            IncomingForce force = incoming.of(enemy, tile, Horizon.NEXT_ROUND);
            if (!force.isEmpty() && force.carriesGround() && publicGain(enemy, tile, force) >= CONTESTED_GAIN) {
                return true;
            }
        }
        return false;
    }

    private double cost(Tile tile, Defenders defenders, boolean immediateOnly) {
        if (!isGuarded(tile)) return 0;
        String key = tile.getPosition() + "|" + immediateOnly + "|" + defenders.key();
        Double known = costs.get(key);
        if (known != null) return known;
        double worst = 0;
        for (Player enemy : enemies()) {
            worst = Math.max(worst, threat(enemy, tile, Horizon.IMMEDIATE, defenders));
            if (!immediateOnly) worst = Math.max(worst, threat(enemy, tile, Horizon.NEXT_ROUND, defenders));
        }
        costs.put(key, worst);
        return worst;
    }

    private double threat(Player enemy, Tile tile, Horizon horizon, Defenders defenders) {
        if (root != null && enemy != exclusion.owner()) return root.threat(enemy, tile, horizon, defenders);
        String key = enemy.getFaction() + "|" + tile.getPosition() + "|" + horizon + "|" + defenders.key();
        return cached(threats, key, () -> computeThreat(enemy, tile, horizon, defenders));
    }

    private double computeThreat(Player enemy, Tile tile, Horizon horizon, Defenders defenders) {
        IncomingForce force = incoming.of(enemy, tile, horizon);
        if (force.isEmpty() || fearCeiling(enemy, tile, horizon, force) < MIN_FEAR) return 0;
        Chances chances = chances(tile, force, defenders);
        double fear = fear(enemy, tile, horizon, force, defenders, chances);
        double target = target(fear);
        double exposed = Math.max(0, chances.take() - target) * stake(tile)
                + Math.max(0, chances.space() - target) * spaceStake(tile, force);
        return weight(enemy, horizon) * fear * exposed;
    }

    private double spaceStake(Tile tile, IncomingForce force) {
        return SPACE_STAKE_SHARE * Math.max(0, spaceGain(tile, force)) + shipLoss(tile);
    }

    double spaceGain(Tile tile, IncomingForce force) {
        String key = force.key() + "|" + tile.getPosition();
        return cached(spaceGains, key, () -> {
            ObjectiveValue view = publicView(force.enemy());
            return view.gain(view.before().after(tile.getPosition(), force.moves(), List.of()));
        });
    }

    double publicGain(Player enemy, Tile tile, IncomingForce force) {
        return publicGain(enemy, tile, force, seatPlanets(tile));
    }

    private double publicGain(Player enemy, Tile tile, IncomingForce force, List<Planet> planets) {
        List<String> names = planets.stream().map(Planet::getName).toList();
        String key = force.key() + "|" + tile.getPosition() + "|" + names;
        return cached(publicGains, key, () -> {
            ObjectiveValue view = publicView(enemy);
            return view.gain(view.before().after(tile.getPosition(), force.moves(), names));
        });
    }

    private double fear(
            Player enemy, Tile tile, Horizon horizon, IncomingForce force, Defenders defenders, Chances chances) {
        double share = appetiteShare(enemy, tile, horizon, force, defenders, chances);
        return fearWith(enemy, tile, horizon, force, share);
    }

    private double fearCeiling(Player enemy, Tile tile, Horizon horizon, IncomingForce force) {
        return fearCeiling(enemy, tile, horizon, force, seatPlanets(tile));
    }

    private double fearCeiling(Player enemy, Tile tile, Horizon horizon, IncomingForce force, List<Planet> planets) {
        String key = force.key() + "|" + tile.getPosition() + "|" + horizon + "|"
                + planets.stream().map(Planet::getName).toList();
        return cached(fearCeilings, key, () -> fearWith(enemy, tile, horizon, force, 1.0, planets));
    }

    private double fearWith(Player enemy, Tile tile, Horizon horizon, IncomingForce force, double appetiteShare) {
        return fearWith(enemy, tile, horizon, force, appetiteShare, seatPlanets(tile));
    }

    private double fearWith(
            Player enemy, Tile tile, Horizon horizon, IncomingForce force, double appetiteShare, List<Planet> planets) {
        double raw = roundFactor(horizon)
                * aggression(enemy)
                * commitment(enemy, force)
                * motiveFactor(enemy, tile, force, planets)
                * appetiteShare
                * ceasefire(enemy);
        return clamp(raw, 0, 1);
    }

    private double roundFactor(Horizon horizon) {
        int round = game.getRound() + (horizon == Horizon.NEXT_ROUND ? 1 : 0);
        if (round <= 1) return 0.3;
        return round == 2 ? 0.6 : 1.0;
    }

    private double aggression(Player enemy) {
        double tech = TECH_PER_STEP * Math.min(TECH_STEPS, militaryTech(enemy));
        double ratio = fleetCostTotal(enemy) / Math.max(1, fleetCostTotal(seat));
        return temper(enemy) + tech + NAVY_WEIGHT * clamp(ratio - 1, 0, 1);
    }

    private double temper(Player enemy) {
        double factor = FactionAggression.of(enemy).factor();
        if (SlayIntent.hunts(game, enemy, seat)) return Math.max(factor, FactionAggression.AGGRESSIVE.factor());
        if (SlayIntent.huntsOthers(game, enemy, seat)) return Math.min(factor, FactionAggression.PASSIVE.factor());
        return factor;
    }

    private static int militaryTech(Player enemy) {
        int warfare = 0;
        for (String id : enemy.getTechs()) {
            TechnologyModel tech = Mapper.getTech(id);
            if (tech != null && tech.isWarfareTech()) warfare++;
        }
        int upgrades = 0;
        for (UnitType type : upgradeable()) {
            if (BoardView.model(enemy, type).map(UnitModel::getIsUpgrade).orElse(false)) upgrades++;
        }
        return warfare + upgrades + (enemy.hasTech(GRAVITY_DRIVE) ? 1 : 0);
    }

    private static List<UnitType> upgradeable() {
        List<UnitType> types = new ArrayList<>(BoardView.MOVING_SHIPS);
        types.add(UnitType.Fighter);
        return types;
    }

    private double commitment(Player enemy, IncomingForce force) {
        double spare = 1 - force.fleetCost() / Math.max(1, fleetCostTotal(enemy));
        return MIN_COMMITMENT + (1 - MIN_COMMITMENT) * clamp(spare / SPARE_FLEET_FOR_FULL_COMMITMENT, 0, 1);
    }

    private double motiveFactor(Player enemy, Tile tile, IncomingForce force, List<Planet> planets) {
        double reason = publicGain(enemy, tile, force, planets) + denial(tile);
        return 0.5 + 0.5 * clamp(reason / SCORING_REASON_SCALE, 0, 1);
    }

    private double denial(Tile tile) {
        if (!isHome(tile) && !isScoringCritical(tile)) return 0;
        return (seatLeads() ? DENIAL_PER_REASON : 0) + (nearWin() ? DENIAL_PER_REASON : 0);
    }

    private boolean isScoringCritical(Tile tile) {
        return scoringLoss(tile) >= SCORING_CRITICAL;
    }

    private double ceasefire(Player enemy) {
        return seat.getPromissoryNotes().containsKey(enemy.getColor() + CEASEFIRE) ? CEASEFIRE_FEAR : 1.0;
    }

    private double appetiteShare(
            Player enemy, Tile tile, Horizon horizon, IncomingForce force, Defenders defenders, Chances chances) {
        double appetite = appetite(enemy, tile, force, defenders, chances);
        double alternative = alternative(enemy, tile) - ACTION_COST;
        double share =
                appetite <= 0 ? 0 : clamp(appetite / Math.max(Math.max(appetite, alternative), APPETITE_SCALE), 0, 1);
        return horizon == Horizon.IMMEDIATE ? share : Math.max(share, NEXT_ROUND_APPETITE_FLOOR);
    }

    private double appetite(Player enemy, Tile tile, IncomingForce force, Defenders defenders, Chances chances) {
        double denial = denial(tile);
        double prize = planetPrize(tile) + publicGain(enemy, tile, force) + denial + (isHome(tile) ? HOME_PRIZE : 0);
        double spaceGain = Math.max(0, spaceGain(tile, force));
        if (isScoringCritical(tile) && !isHome(tile)) spaceGain += denial + SCORING_LOSS_SHARE * scoringLoss(tile);
        double space = chances.space();
        double attackerLoss = (1 - space) * force.fleetCost()
                + space * (1 - space) * Math.min(force.fleetCost(), defenders.shipCost(seat))
                + space * (1 - chances.groundMax()) * force.groundCost();
        return chances.take() * prize + space * spaceGain - attackerLoss - actionCost(enemy);
    }

    private double actionCost(Player enemy) {
        return IncomingForces.holdsUnplayedThundersEdgeWarfare(game, enemy) ? THUNDERS_EDGE_ACTION_COST : ACTION_COST;
    }

    double attackLikelihood(Player enemy, Tile tile, Horizon horizon, double netGain) {
        double alternative = Math.max(alternative(enemy, tile) - ACTION_COST, APPETITE_SCALE);
        double share = attackShare(netGain - actionCost(enemy), alternative);
        return clamp(roundFactor(horizon) * aggression(enemy) * share * ceasefire(enemy), 0, 1);
    }

    static double attackShare(double appetite, double alternative) {
        double eagerness = softPositive(appetite);
        return eagerness / (eagerness + alternative);
    }

    private static double softPositive(double value) {
        double scaled = value / APPETITE_MARGIN;
        if (scaled > SOFT_POSITIVE_LINEAR) return value;
        return APPETITE_MARGIN * Math.log1p(Math.exp(scaled));
    }

    private double planetPrize(Tile tile) {
        return seatPlanets(tile).stream()
                .mapToDouble(BoardView::planetValue)
                .max()
                .orElse(0);
    }

    private double alternative(Player enemy, Tile excluded) {
        Map<String, Double> byTile = alternatives.get(enemy.getFaction());
        if (byTile == null) {
            byTile = alternativesFor(enemy);
            alternatives.put(enemy.getFaction(), byTile);
        }
        double best = 0;
        for (Map.Entry<String, Double> entry : byTile.entrySet()) {
            if (!entry.getKey().equals(excluded.getPosition())) best = Math.max(best, entry.getValue());
        }
        return best;
    }

    // TODO: ignores the enemy's token-locked origins and other players' ships in the free system (less defensive)
    private Map<String, Double> alternativesFor(Player enemy) {
        Map<String, Double> values = new HashMap<>();
        ObjectiveValue view = publicView(enemy);
        for (Tile origin : game.getTileMap().values()) {
            int move = carrierMove(enemy, origin);
            if (move <= 0) continue;
            for (String position : incoming.reach(enemy, origin, move).keySet()) {
                if (values.containsKey(position)) continue;
                Tile tile = game.getTileByPosition(position);
                List<Planet> free = tile == null ? List.of() : freePlanets(tile);
                if (free.isEmpty()) continue;
                double value = free.stream().mapToDouble(BoardView::planetValue).sum();
                List<String> names = free.stream().map(Planet::getName).toList();
                value += Math.max(0, view.gain(view.before().after(position, List.of(), names)));
                values.put(position, value);
            }
        }
        return values;
    }

    private List<Planet> freePlanets(Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                .filter(planet -> !BoardView.hasCustodians(planet))
                .toList();
    }

    private static int carrierMove(Player enemy, Tile origin) {
        boolean hasGround = BoardView.countInTile(origin, enemy, UnitType.Infantry)
                        + BoardView.countInTile(origin, enemy, UnitType.Mech)
                > 0;
        if (!hasGround) return 0;
        return BoardView.ships(BoardView.space(origin), enemy).keySet().stream()
                .filter(type -> type != UnitType.Fighter && BoardView.capacity(enemy, type) > 0)
                .mapToInt(type -> BoardView.moveValue(enemy, type))
                .max()
                .orElse(0);
    }

    Chances chances(Tile tile, IncomingForce force, Defenders defenders) {
        double space = spaceChance(tile, force, defenders);
        List<Planet> planets = seatPlanets(tile);
        if (!force.carriesGround() || planets.isEmpty()) return new Chances(space, 0, 0);
        double groundMax = 0;
        for (Planet planet : planets) {
            groundMax = Math.max(groundMax, groundChance(tile, force, planet, defenders));
        }
        return new Chances(space, space * groundMax, groundMax);
    }

    private double spaceChance(Tile tile, IncomingForce force, Defenders defenders) {
        String key = tile.getPosition() + "|" + force.key() + "|" + defenders.shipsKey();
        return cached(spaceChances, key, () -> computeSpace(tile, force, defenders));
    }

    private double computeSpace(Tile tile, IncomingForce force, Defenders defenders) {
        Optional<SpaceFight> fight = spaceFight(tile, force, defenders);
        if (fight.isPresent())
            return CombatOdds.resolve(fight.get().attack(), fight.get().defence())
                    .attackerWins();
        return undefendedChance(tile, force);
    }

    double expectedShipLoss(Tile tile, IncomingForce force, Defenders defenders) {
        Optional<SpaceFight> fight = spaceFight(tile, force, defenders);
        if (fight.isEmpty()) return undefendedChance(tile, force) * defenders.shipCost(seat);
        return CombatOdds.resolveDetailed(fight.get().attack(), fight.get().defence())
                .defenderCostLost();
    }

    double expectedAttackerLoss(Tile tile, IncomingForce force, Defenders defenders) {
        Optional<SpaceFight> fight = spaceFight(tile, force, defenders);
        if (fight.isEmpty()) return 0;
        return CombatOdds.resolveDetailed(fight.get().attack(), fight.get().defence())
                .attackerCostLost();
    }

    private double undefendedChance(Tile tile, IncomingForce force) {
        Player enemy = force.enemy();
        int ourCannon = (int) Math.round(BattleMath.cannonFire(game, tile, seat, null));
        List<Combatant> arriving = CombatForces.combatants(
                game,
                tile,
                BoardView.space(tile),
                new Side(enemy, force.ships()),
                new Side(seat, Map.of()),
                force.damaged());
        return BattleMath.afterCannonFire(arriving, ourCannon).isEmpty() ? 0 : 1;
    }

    private Optional<SpaceFight> spaceFight(Tile tile, IncomingForce force, Defenders defenders) {
        Player enemy = force.enemy();
        UnitHolder space = BoardView.space(tile);
        int ourCannon = (int) Math.round(BattleMath.cannonFire(game, tile, seat, null));
        int theirCannon = (int) Math.round(BattleMath.cannonFire(game, tile, enemy, null));
        Side attacking = new Side(enemy, force.ships());
        Side defending = new Side(seat, defenders.ships().units());
        Map<UnitType, Integer> defendersDamaged = defenders.ships().damaged();
        boolean undefended = defenders.ships().isEmpty()
                || CombatForces.combatants(game, tile, space, defending, attacking, defendersDamaged)
                        .isEmpty();
        if (undefended) return Optional.empty();
        Side cannonedDefenders = BattleMath.afterAssaultCannon(defending, attacking);
        Side cannonedAttackers = BattleMath.afterAssaultCannon(attacking, defending);
        Side barragedDefenders = BattleMath.afterBarrage(cannonedDefenders, cannonedAttackers);
        Side barragedAttackers = BattleMath.afterBarrage(cannonedAttackers, cannonedDefenders);
        List<Combatant> defenderUnits = BattleMath.afterCannonFire(
                CombatForces.combatants(game, tile, space, barragedDefenders, barragedAttackers, defendersDamaged),
                theirCannon);
        List<Combatant> attackers = BattleMath.afterCannonFire(
                CombatForces.combatants(game, tile, space, barragedAttackers, barragedDefenders, force.damaged()),
                ourCannon);
        return Optional.of(new SpaceFight(
                CombatForces.force(enemy, attackers, false), CombatForces.force(seat, defenderUnits, false)));
    }

    private double groundChance(Tile tile, IncomingForce force, Planet planet, Defenders defenders) {
        String key = tile.getPosition() + "|" + force.key() + "|" + planet.getName() + "|"
                + defenders.groundKey(planet.getName());
        return cached(groundChances, key, () -> computeGround(tile, force, planet, defenders));
    }

    private double computeGround(Tile tile, IncomingForce force, Planet planet, Defenders defenders) {
        Player enemy = force.enemy();
        Map<UnitType, Integer> landing = new EnumMap<>(UnitType.class);
        landing.putAll(force.ground());
        removeLandingForces(landing, L4Disruptors.shieldsInvaders(enemy) ? 0 : expectedSpaceCannonHits(planet));
        if (landing.values().stream().mapToInt(Integer::intValue).sum() == 0) return 0;
        Defenders.Stack ours = defenders.on(planet.getName());
        if (ours.isEmpty()) return 1;
        Side attacking = new Side(enemy, landing);
        Side defending = new Side(seat, ours.units());
        Force defence = CombatForces.force(
                seat,
                BattleMath.afterCannonFire(
                        CombatForces.combatants(game, tile, planet, defending, attacking, ours.damaged()),
                        bombardmentHits(force, planet)),
                true);
        Force attack = CombatForces.force(
                enemy, CombatForces.combatants(game, tile, planet, attacking, defending, Map.of()), true);
        return CombatOdds.resolve(attack, defence).attackerWins();
    }

    private int bombardmentHits(IncomingForce force, Planet planet) {
        Player enemy = force.enemy();
        if (BombardmentRules.shielded(game, enemy, planet, force.ships())) return 0;
        return BombardmentRules.expectedHits(enemy, force.ships());
    }

    private int expectedSpaceCannonHits(Planet planet) {
        double expected = 0;
        for (UnitKey key : planet.getUnitKeysForPlayer(seat)) {
            UnitModel model = seat.getUnitFromUnitKey(key);
            if (model == null) continue;
            int dice = model.getSpaceCannonDieCount(seat) * planet.getUnitCount(key);
            expected += dice * BattleMath.hitChance(model.getSpaceCannonHitsOn(seat));
        }
        return (int) Math.round(expected);
    }

    private static void removeLandingForces(Map<UnitType, Integer> landing, int hits) {
        int left = hits;
        for (UnitType type : List.of(UnitType.Infantry, UnitType.Mech)) {
            int removed = Math.min(left, landing.getOrDefault(type, 0));
            if (removed > 0) landing.merge(type, -removed, Integer::sum);
            left -= removed;
        }
    }

    private Bundle bestKeptBundle(
            Tile origin, Defenders afterRegardless, List<UnitMove> leaving, double now, @Nullable Blend blend) {
        Bundle best = null;
        for (UnitType type : typesOf(leaving)) {
            int available = leaving.stream()
                    .filter(move -> move.type() == type)
                    .mapToInt(UnitMove::count)
                    .sum();
            for (int count : bundleSizes(available)) {
                List<UnitMove> remaining = without(leaving, type, count);
                double after = blendedCost(origin, afterRegardless.minus(movesFrom(origin, remaining)), blend);
                double unitCost = Math.max(UNIT_COST_FLOOR, count * IncomingForces.cost(seat, type));
                Bundle bundle = new Bundle(
                        now - after, (now - after) / unitCost, BoardView.capacity(seat, type), unitCost, remaining);
                if (best == null || better(bundle, best)) best = bundle;
            }
        }
        return best;
    }

    private static boolean better(Bundle candidate, Bundle best) {
        int byScore = Double.compare(candidate.score(), best.score());
        if (byScore != 0) return byScore > 0;
        int byCapacity = Integer.compare(best.capacity(), candidate.capacity());
        if (byCapacity != 0) return byCapacity > 0;
        return candidate.cost() < best.cost();
    }

    private static List<Integer> bundleSizes(int available) {
        List<Integer> sizes = new ArrayList<>();
        for (int count = 1; count <= Math.min(BUNDLE_SIZES, available); count++) sizes.add(count);
        if (available > BUNDLE_SIZES) sizes.add(available);
        return sizes;
    }

    private static List<UnitType> typesOf(List<UnitMove> moves) {
        return moves.stream().map(UnitMove::type).distinct().toList();
    }

    static List<UnitMove> without(List<UnitMove> moves, UnitType type, int count) {
        List<UnitMove> result = new ArrayList<>();
        int left = count;
        for (UnitMove move : moves) {
            if (move.type() != type || left <= 0) {
                result.add(move);
                continue;
            }
            int removed = Math.min(left, move.count());
            left -= removed;
            if (move.count() > removed) {
                result.add(new UnitMove(move.origin(), move.holder(), move.type(), move.count() - removed));
            }
        }
        return result;
    }

    private static List<UnitMove> movesFrom(Tile origin, List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> move.origin().equals(origin.getPosition()))
                .toList();
    }

    private List<Planet> seatPlanets(Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getPlanets().contains(planet.getName()))
                .toList();
    }

    private List<Player> enemies() {
        if (enemies == null) {
            enemies = game.getRealPlayers().stream()
                    .filter(player -> player != seat)
                    .toList();
        }
        return enemies;
    }

    @Nullable
    private Tile home() {
        if (!homeKnown) {
            home = seat.getHomeSystemTile();
            homeKnown = true;
        }
        return home;
    }

    private ObjectiveValue own() {
        if (root != null) return root.own();
        if (own == null) own = new ObjectiveValue(game, seat);
        return own;
    }

    private ObjectiveValue publicView(Player player) {
        ObjectiveValue view = publicViews.get(player.getFaction());
        if (view == null) {
            view = ObjectiveValue.publicOnly(game, player);
            publicViews.put(player.getFaction(), view);
        }
        return view;
    }

    double fleetCostTotal(Player player) {
        String key = player.getFaction();
        Double known = fleetCosts.get(key);
        if (known != null) return known;
        double total = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (exclusion != null && exclusion.owner() == player && exclusion.tile() == tile) continue;
            for (Map.Entry<UnitType, Integer> ship :
                    BoardView.ships(BoardView.space(tile), player).entrySet()) {
                if (ship.getKey() != UnitType.Fighter) {
                    total += ship.getValue() * IncomingForces.cost(player, ship.getKey());
                }
            }
        }
        fleetCosts.put(key, total);
        return total;
    }

    private boolean seatLeads() {
        int mine = seat.getTotalVictoryPoints();
        return mine >= 1
                && game.getRealPlayers().stream()
                        .filter(player -> player != seat)
                        .allMatch(player -> player.getTotalVictoryPoints() <= mine);
    }

    private boolean nearWin() {
        return seat.getTotalVictoryPoints() >= game.getVp() - NEAR_WIN_GAP;
    }

    private static double cached(Map<String, Double> cache, String key, Supplier<Double> compute) {
        Double known = cache.get(key);
        if (known != null) return known;
        double value = compute.get();
        cache.put(key, value);
        return value;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
