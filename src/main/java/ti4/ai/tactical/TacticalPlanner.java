package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.MovementGraph;
import ti4.ai.explore.ExploreDeck;
import ti4.ai.explore.ExploreOutlook;
import ti4.ai.explore.ExploreSite;
import ti4.ai.promissory.PlayAreaNotes;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.UnitModel;

@UtilityClass
public class TacticalPlanner {

    static final double MIN_SCORE = 1.0;
    static final double ATTACK_MIN_WIN = 0.8;
    static final double ATTACK_MIN_SCORE = 4.0;
    private static final double DISTANCE_COST = 0.2;
    private static final double LAST_HOME_SHIP_COST = 1.5;
    private static final double TECH_STEAL_VALUE = 3.0;
    private static final double PLANET_TEMPO_VALUE = 0.8;
    private static final double FRONTIER_CARD_VALUE = 1.5;
    private static final double FRONTIER_POSITION_VALUE = 1.0;
    private static final String DARK_ENERGY_TAP = "det";
    private static final int LAST_EARLY_ROUND = 4;
    private static final double EARLY_GAME_FADE_PER_ROUND = 0.25;
    private static final double CUSTODIANS_VALUE = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final String DARKEN_THE_SKIES = "dts";
    private static final String CONQUER_THE_WEAK = "conquer";
    private static final String MAKE_AN_EXAMPLE = "mew";
    private static final String ASSAULT_CANNON = "asc";
    private static final int ASSAULT_CANNON_SHIPS = 3;
    private static final int DEMONSTRATION_SHIPS = 3;
    private static final double GROUP_MOVE_COST = 0.3;
    private static final double SHIP_COST_WEIGHT = 0.05;
    private static final double HOME_DEFENCE_VALUE = 6.0;
    private static final int CUSTODIANS_COST = 6;
    private static final int HOME_GARRISON = 1;
    private static final int MECATOL_GARRISON = 1;
    private static final List<UnitType> TRANSPORT_PREFERENCE =
            List.of(UnitType.Carrier, UnitType.Flagship, UnitType.Dreadnought, UnitType.Warsun);
    private static final List<UnitType> GROUND_FORCE_ORDER = List.of(UnitType.Infantry, UnitType.Mech);
    private static final List<UnitType> POSITION_SHIPS = List.of(
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);

    public static Optional<TacticalPlan> best(Game game, Player seat) {
        if (seat.getTacticalCC() <= 0) return Optional.empty();
        int reservedTokens = ScoringReserve.of(game, seat).tokens();
        if (seat.getTacticalCC() + seat.getStrategicCC() - 1 < reservedTokens) return Optional.empty();
        Context context = new Context(game, seat);
        List<TacticalPlan> plans = game.getTileMap().values().stream()
                .filter(tile -> !tile.hasPlayerCC(seat))
                .filter(tile -> ButtonHelper.canActivateTile(game, seat, tile))
                .map(tile -> bestFor(context, tile))
                .flatMap(Optional::stream)
                .filter(plan -> plan.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(TacticalPlan::score).reversed())
                .toList();
        if (plans.isEmpty()) return Optional.empty();
        TacticalPlan top = plans.getFirst();
        boolean anotherActionAfter =
                seat.getTacticalCC() >= 2 && seat.getTacticalCC() + seat.getStrategicCC() - 2 >= reservedTokens;
        if (top.kind() != Kind.PRODUCE || !anotherActionAfter) return Optional.of(top);
        return plans.stream()
                .filter(plan -> plan.kind() != Kind.PRODUCE)
                .filter(plan ->
                        plan.moves().stream().anyMatch(move -> move.origin().equals(top.target())))
                .findFirst()
                .or(() -> Optional.of(top));
    }

    public static Optional<TacticalPlan> bestForWarfare(Game game, Player seat) {
        Context context = new Context(game, seat);
        return game.getTileMap().values().stream()
                .filter(tile -> tile.hasPlayerCC(seat) || ButtonHelper.canActivateTile(game, seat, tile))
                .map(tile -> bestFor(context, tile))
                .flatMap(Optional::stream)
                .filter(plan -> plan.score() >= MIN_SCORE)
                .max(Comparator.comparingDouble(TacticalPlan::score));
    }

    public static boolean worthProducingAt(Game game, Player seat, Tile tile) {
        return produce(new Context(game, seat), tile)
                .filter(plan -> plan.score() >= MIN_SCORE)
                .isPresent();
    }

    public static Optional<TacticalPlan> forTarget(Game game, Player seat, String target) {
        Tile tile = game.getTileByPosition(target);
        return tile == null ? Optional.empty() : bestFor(new Context(game, seat), tile);
    }

    private static Optional<TacticalPlan> bestFor(Context context, Tile tile) {
        if (!MovementGraph.canEnter(context.game, context.seat, tile)) return Optional.empty();
        List<TacticalPlan> plans = new ArrayList<>();
        expand(context, tile).ifPresent(plans::add);
        attack(context, tile).ifPresent(plans::add);
        produce(context, tile).ifPresent(plans::add);
        position(context, tile).ifPresent(plans::add);
        return plans.stream()
                .map(plan -> plan.withScore(PlayAreaNotes.adjustedScore(context.game, context.seat, tile, plan)))
                .max(Comparator.comparingDouble(TacticalPlan::score));
    }

    private static Optional<TacticalPlan> expand(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (BoardView.hasEnemyUnits(game, seat, tile)) return Optional.empty();
        List<Planet> free = tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                .filter(planet -> !BoardView.hasCustodians(planet) || context.canPayCustodians())
                .sorted(Comparator.comparingDouble(BoardView::planetValue).reversed())
                .toList();
        if (free.isEmpty()) return Optional.empty();
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            Optional<TacticalPlan> plan = expandFrom(context, tile, origin, free);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        if (best == null || context.coveredByEnemySpaceCannon(tile)) return Optional.empty();
        return Optional.of(best);
    }

    private static Optional<TacticalPlan> expandFrom(Context context, Tile tile, Tile origin, List<Planet> free) {
        Player seat = context.seat;
        Optional<UnitType> transport = TRANSPORT_PREFERENCE.stream()
                .filter(type -> BoardView.undamaged(BoardView.space(origin), seat, type) > 0)
                .filter(type -> BoardView.capacity(seat, type) > 0)
                .filter(type -> context.reachesWithGravityDrive(origin, tile, type))
                .findFirst();
        if (transport.isEmpty()) return Optional.empty();
        List<UnitMove> groundForces = availableGroundForces(context.game, seat, origin);
        int loadable = Math.min(BoardView.capacity(seat, transport.get()), unitCount(groundForces));
        int landed = Math.min(loadable, free.size());
        if (landed == 0) return Optional.empty();
        int carried = isLastCarrierLeavingHome(context, origin, transport.get()) ? loadable : landed;
        Optional<List<UnitMove>> legal = loadedMoves(
                        context, origin, tile, transport.get(), take(groundForces, carried))
                .or(() -> loadedMoves(context, origin, tile, transport.get(), take(groundForces, landed)));
        if (legal.isEmpty()) return Optional.empty();
        Map<String, Integer> landings = new LinkedHashMap<>();
        double value = 0;
        for (Planet planet : free.subList(0, landed)) {
            landings.put(planet.getName(), 1);
            value += BoardView.planetValue(planet) + planetTempo(context.game) + context.exploreValue(tile, planet);
            if (BoardView.hasCustodians(planet)) value += CUSTODIANS_VALUE;
        }
        value += context.objectiveGain(tile, legal.get(), landings.keySet());
        double score = value
                - DISTANCE_COST * context.distance(origin, tile, transport.get())
                - (leavesHomeWithoutShips(context, origin, legal.get()) ? LAST_HOME_SHIP_COST : 0);
        return Optional.of(new TacticalPlan(Kind.EXPAND, tile.getPosition(), legal.get(), landings, score));
    }

    private static Optional<List<UnitMove>> loadedMoves(
            Context context, Tile origin, Tile tile, UnitType transport, List<UnitMove> cargo) {
        List<UnitMove> moves = new ArrayList<>();
        moves.add(new UnitMove(origin.getPosition(), BoardView.SPACE, transport, 1));
        moves.addAll(cargo);
        return legalMoves(context, origin, tile, moves);
    }

    private static boolean isLastCarrierLeavingHome(Context context, Tile origin, UnitType transport) {
        if (!origin.isHomeSystem(context.game)) return false;
        int departing = transport == UnitType.Carrier ? 1 : 0;
        return BoardView.count(BoardView.space(origin), context.seat, UnitType.Carrier) == departing;
    }

    private static Optional<TacticalPlan> attack(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (isAnotherPlayersHome(game, seat, tile) && !opensHomeSystems(game, seat)) return Optional.empty();
        Player opponent = singleOpponent(game, seat, tile);
        if (opponent == null) return Optional.empty();
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            Optional<TacticalPlan> plan = attackFrom(context, tile, origin, opponent);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        if (best == null) return Optional.empty();
        boolean firesThrough = isAnotherPlayersHome(game, seat, tile) && opensHomeSystems(game, seat);
        if (context.coveredByEnemySpaceCannon(tile) && !firesThrough) return Optional.empty();
        return Optional.of(best);
    }

    private static Optional<TacticalPlan> attackFrom(Context context, Tile tile, Tile origin, Player opponent) {
        Optional<TacticalPlan> unescorted = attackFrom(context, tile, origin, opponent, 0);
        int fighters = BoardView.count(BoardView.space(origin), context.seat, UnitType.Fighter);
        if (fighters == 0 || !BoardView.hasEnemyShips(context.game, context.seat, tile)) return unescorted;
        Optional<TacticalPlan> escorted = attackFrom(context, tile, origin, opponent, fighters);
        if (escorted.isEmpty()) return unescorted;
        if (unescorted.isEmpty()) return escorted;
        return escorted.get().score() > unescorted.get().score() ? escorted : unescorted;
    }

    private static Optional<TacticalPlan> attackFrom(
            Context context, Tile tile, Tile origin, Player opponent, int fightersAvailable) {
        Game game = context.game;
        Player seat = context.seat;
        UnitHolder originSpace = BoardView.space(origin);
        List<UnitMove> ships = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            int available = BoardView.count(originSpace, seat, type);
            if (available == 0 || !context.reaches(origin, tile, type)) continue;
            ships.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, available));
        }
        Comparator<UnitType> preference = BoardView.hasEnemyShips(game, seat, tile)
                ? Comparator.comparingDouble(type -> cost(seat, type))
                : Comparator.comparingInt((UnitType type) -> BoardView.capacity(seat, type))
                        .thenComparingDouble(type -> -cost(seat, type));
        gravityDriveShip(
                        context,
                        origin,
                        tile,
                        BoardView.MOVING_SHIPS,
                        type -> BoardView.count(originSpace, seat, type),
                        preference)
                .ifPresent(ships::add);
        if (ships.isEmpty()) return Optional.empty();
        if (origin.isHomeSystem(game)) keepOneShipHome(seat, origin, ships);
        if (ships.isEmpty()) return Optional.empty();
        int escort = Math.min(fightersAvailable, capacityOf(seat, ships));
        if (escort > 0) ships.add(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, escort));
        double spaceWin = 1.0;
        boolean spaceCombat = BoardView.hasEnemyShips(game, seat, tile);
        UnitHolder space = BoardView.space(tile);
        Side attacking = new Side(seat, shipsOf(ships));
        Map<UnitType, Integer> attackersDamaged = damagedAmong(originSpace, seat, attacking.units());
        int cannonHits = isAnotherPlayersHome(game, seat, tile) && context.coveredByEnemySpaceCannon(tile)
                ? expectedCannonHits(game, seat, tile)
                : 0;
        double cannonLosses = 0;
        if (cannonHits > 0) {
            List<Combatant> arriving = CombatForces.combatants(
                    game, tile, space, attacking, new Side(opponent, Map.of()), attackersDamaged);
            List<Combatant> survivors = afterCannonFire(arriving, cannonHits);
            if (survivors.isEmpty()) return Optional.empty();
            cannonLosses = arriving.stream().mapToDouble(Combatant::cost).sum()
                    - survivors.stream().mapToDouble(Combatant::cost).sum();
        }
        if (spaceCombat) {
            Side defending = new Side(opponent, BoardView.ships(space, opponent));
            Map<UnitType, Integer> defendersDamaged = damagedAmong(space, opponent, defending.units());
            if (CombatForces.combatants(game, tile, space, defending, attacking, defendersDamaged)
                    .isEmpty()) return Optional.empty();
            Side cannonedDefenders = afterAssaultCannon(defending, attacking);
            Side cannonedAttackers = afterAssaultCannon(attacking, defending);
            Side barragedDefenders = afterBarrage(cannonedDefenders, cannonedAttackers);
            Side barragedAttackers = afterBarrage(cannonedAttackers, cannonedDefenders);
            List<Combatant> defenders = afterCannonFire(
                    CombatForces.combatants(game, tile, space, barragedDefenders, barragedAttackers, defendersDamaged),
                    ownCannonHits(game, seat, tile, origin));
            List<Combatant> attackers = afterCannonFire(
                    CombatForces.combatants(game, tile, space, barragedAttackers, barragedDefenders, attackersDamaged),
                    cannonHits);
            Force defence = CombatForces.force(opponent, defenders, false);
            Force attack = CombatForces.withExpectedCopy(
                    game, seat, opponent, CombatForces.force(seat, attackers, false), defence, true, false);
            spaceWin = CombatOdds.resolve(attack, defence).attackerWins();
            if (spaceWin < ATTACK_MIN_WIN) return Optional.empty();
        }
        Map<UnitType, Integer> bombarding = new EnumMap<>(UnitType.class);
        bombarding.putAll(BoardView.ships(space, seat));
        attacking.units().forEach((type, count) -> bombarding.merge(type, count, Integer::sum));
        Optional<BombardmentRules.Target> bombardment = BombardmentRules.target(game, seat, tile, bombarding);
        List<UnitMove> cargo = take(availableGroundForces(game, seat, origin), capacityOf(seat, ships) - escort);
        int assigned = 0;
        Map<String, Integer> landings = new LinkedHashMap<>();
        double value = 0;
        boolean groundCombat = false;
        boolean conquering = tile.isHomeSystem(game) && wantsToConquer(game, seat);
        for (Planet planet : enemyPlanets(game, seat, tile)) {
            int ground = unitCount(cargo) - assigned;
            if (ground == 0 || (BoardView.enemyStructuresOn(game, seat, planet) && !conquering)) continue;
            int defenders = BoardView.groundForces(planet, opponent);
            int sent = defenders == 0 ? 1 : ground;
            boolean bombarded = bombardment
                    .filter(target -> target.planet().equals(planet.getName()))
                    .isPresent();
            int bombardmentHits = bombarded ? BombardmentRules.expectedHits(seat, bombarding) : 0;
            double groundWin = defenders == 0
                    ? 1.0
                    : groundOdds(game, tile, planet, seat, opponent, unitsOf(skip(cargo, assigned)), bombardmentHits);
            if (groundWin < ATTACK_MIN_WIN) continue;
            landings.put(planet.getName(), sent);
            assigned += sent;
            value += (BoardView.planetValue(planet) + unexploredValue(context, tile, planet)) * groundWin;
            groundCombat |= defenders > 0;
        }
        if ((spaceCombat || groundCombat) && canStealTech(game, seat, opponent)) value += TECH_STEAL_VALUE;
        value += actionSecretBonus(game, seat, tile, opponent, ships, spaceCombat, groundCombat);
        value += makeAnExampleBonus(game, seat, tile, bombarding, bombardment);
        if (spaceCombat && tile == seat.getHomeSystemTile()) value += HOME_DEFENCE_VALUE;
        int landed = landings.values().stream().mapToInt(Integer::intValue).sum();
        List<UnitMove> moves = new ArrayList<>(ships);
        moves.addAll(take(cargo, landed));
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, moves);
        if (legal.isEmpty()) return Optional.empty();
        value += spaceWin * context.objectiveGain(tile, legal.get(), landings.keySet());
        if (value <= 0) return Optional.empty();
        double score = spaceWin * value - (1 - spaceWin) * fleetCost(seat, legal.get()) - cannonLosses;
        if (score < ATTACK_MIN_SCORE) return Optional.empty();
        return Optional.of(new TacticalPlan(Kind.ATTACK, tile.getPosition(), legal.get(), landings, score));
    }

    private static double unexploredValue(Context context, Tile tile, Planet planet) {
        return BoardView.controller(context.game, planet.getName()) == null ? context.exploreValue(tile, planet) : 0;
    }

    private static Optional<UnitMove> gravityDriveShip(
            Context context,
            Tile origin,
            Tile tile,
            Collection<UnitType> types,
            ToIntFunction<UnitType> available,
            Comparator<UnitType> preference) {
        return types.stream()
                .filter(type -> available.applyAsInt(type) > 0)
                .filter(type ->
                        !context.reaches(origin, tile, type) && context.reachesWithGravityDrive(origin, tile, type))
                .max(preference)
                .map(type -> new UnitMove(origin.getPosition(), BoardView.SPACE, type, 1));
    }

    private static double cost(Player seat, UnitType type) {
        return BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
    }

    private static double makeAnExampleBonus(
            Game game,
            Player seat,
            Tile tile,
            Map<UnitType, Integer> bombarding,
            Optional<BombardmentRules.Target> bombardment) {
        if (!seat.getSecretsUnscored().containsKey(MAKE_AN_EXAMPLE) || bombardment.isEmpty()) return 0;
        if (!(tile.getUnitHolders().get(bombardment.get().planet()) instanceof Planet planet)) return 0;
        return ObjectiveValue.VICTORY_POINT_VALUE
                * BombardmentRules.chanceToDestroyAll(
                        seat, bombarding, planet, bombardment.get().defender());
    }

    private static int expectedCannonHits(Game game, Player seat, Tile tile) {
        double expected = 0;
        for (Player other : game.getRealPlayers()) {
            if (other != seat) expected += cannonFire(game, tile, other, null);
        }
        return (int) Math.round(expected);
    }

    private static int ownCannonHits(Game game, Player seat, Tile tile, Tile origin) {
        return (int) Math.round(cannonFire(game, tile, seat, BoardView.space(origin)));
    }

    private static double cannonFire(Game game, Tile tile, Player shooter, @Nullable UnitHolder leaving) {
        double expected = 0;
        for (String position : FoWHelper.getAdjacentTiles(game, tile.getPosition(), shooter, false, true)) {
            Tile nearby = game.getTileByPosition(position);
            if (nearby == null) continue;
            boolean inSystem = nearby == tile;
            for (UnitHolder holder : nearby.getUnitHolders().values()) {
                if (holder == leaving) continue;
                for (UnitKey key : holder.getUnitKeysForPlayer(shooter)) {
                    UnitModel model = shooter.getUnitFromUnitKey(key);
                    if (model == null || (!inSystem && !model.getDeepSpaceCannon(shooter))) continue;
                    int dice = model.getSpaceCannonDieCount(shooter) * holder.getUnitCount(key);
                    expected += dice * hitChance(model.getSpaceCannonHitsOn(shooter));
                }
            }
        }
        return expected;
    }

    private static Side afterBarrage(Side target, Side shooter) {
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

    private static Side afterAssaultCannon(Side target, Side shooter) {
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

    private static boolean firesAssaultCannon(Side side) {
        int nonFighterShips = side.units().entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(Map.Entry::getValue)
                .sum();
        return side.player().hasTech(ASSAULT_CANNON) && nonFighterShips >= ASSAULT_CANNON_SHIPS;
    }

    private static double hitChance(int hitsOn) {
        return Math.max(0, 11 - hitsOn) / 10.0;
    }

    private static Map<UnitType, Integer> damagedAmong(UnitHolder holder, Player player, Map<UnitType, Integer> units) {
        Map<UnitType, Integer> damaged = new EnumMap<>(UnitType.class);
        units.forEach((type, count) -> {
            int hurt = Math.min(count, BoardView.damaged(holder, player, type));
            if (hurt > 0) damaged.put(type, hurt);
        });
        return damaged;
    }

    private static List<Combatant> afterCannonFire(List<Combatant> ships, int hits) {
        List<Combatant> remaining = new ArrayList<>(ships);
        remaining.sort(Comparator.comparingDouble(Combatant::cost));
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

    private static boolean isAnotherPlayersHome(Game game, Player seat, Tile tile) {
        return tile.isHomeSystem(game) && tile != seat.getHomeSystemTile();
    }

    private static boolean opensHomeSystems(Game game, Player seat) {
        return seat.getSecretsUnscored().containsKey(DARKEN_THE_SKIES) || wantsToConquer(game, seat);
    }

    private static boolean wantsToConquer(Game game, Player seat) {
        return game.getRevealedPublicObjectives().containsKey(CONQUER_THE_WEAK)
                && !ObjectivePolicy.hasScored(game, seat, CONQUER_THE_WEAK);
    }

    private static double actionSecretBonus(
            Game game,
            Player seat,
            Tile tile,
            Player opponent,
            List<UnitMove> ships,
            boolean spaceCombat,
            boolean groundCombat) {
        if (!spaceCombat && !groundCombat) return 0;
        java.util.Set<String> held = seat.getSecretsUnscored().keySet();
        int qualifying = 0;
        if (held.contains("sar") && opponent.getTotalVictoryPoints() == game.getHighestScore()) qualifying++;
        if (held.contains("btv") && tile.isAnomaly(game, seat)) qualifying++;
        if (held.contains(DARKEN_THE_SKIES) && tile.isHomeSystem(game) && tile != seat.getHomeSystemTile()) {
            qualifying++;
        }
        if (held.contains("baf")
                && seat.getPromissoryNotesInPlayArea().stream().anyMatch(note -> game.getPNOwner(note) == opponent)) {
            qualifying++;
        }
        if (spaceCombat) {
            UnitHolder space = BoardView.space(tile);
            int arriving = movedNonFighterShips(ships) + BoardView.nonFighterShips(space, seat);
            boolean flagship = ships.stream().anyMatch(move -> move.type() == UnitType.Flagship);
            if (held.contains("uf") && flagship) qualifying++;
            if (held.contains("dyp") && arriving >= DEMONSTRATION_SHIPS) qualifying++;
            if (held.contains("dtgs")
                    && BoardView.count(space, opponent, UnitType.Flagship)
                                    + BoardView.count(space, opponent, UnitType.Warsun)
                            > 0) {
                qualifying++;
            }
        }
        int combats = (spaceCombat ? 1 : 0) + (groundCombat ? 1 : 0);
        return ObjectiveValue.VICTORY_POINT_VALUE * Math.min(qualifying, combats);
    }

    private static Optional<TacticalPlan> produce(Context context, Tile tile) {
        Player seat = context.seat;
        if (!hasDock(seat, tile)) return Optional.empty();
        ProductionPlanner.BuildPlan build = ProductionPlanner.plan(context.game, seat, tile);
        if (build.isEmpty()) return Optional.empty();
        double objectiveGain = context.builtGain(tile, build);
        boolean forScoring = build.scoring() || objectiveGain > 0;
        if (build.units() < ProductionPlanner.MIN_UNITS && !forScoring) return Optional.empty();
        return Optional.of(
                new TacticalPlan(Kind.PRODUCE, tile.getPosition(), List.of(), Map.of(), build.value() + objectiveGain));
    }

    private static double planetTempo(Game game) {
        return PLANET_TEMPO_VALUE * earlyGameWeight(game);
    }

    static double earlyGameWeight(Game game) {
        int roundsPastEarlyGame = Math.max(0, game.getRound() - LAST_EARLY_ROUND);
        return Math.max(0, 1 - EARLY_GAME_FADE_PER_ROUND * roundsPastEarlyGame);
    }

    private static Optional<TacticalPlan> position(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        double frontier = frontierExplore(seat, tile);
        if (!context.objectives().caresAboutPresence() && frontier == 0) return Optional.empty();
        if (BoardView.hasEnemyShips(game, seat, tile) || context.coveredByEnemySpaceCannon(tile)) {
            return Optional.empty();
        }
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            for (UnitType type : POSITION_SHIPS) {
                if (BoardView.undamaged(BoardView.space(origin), seat, type) == 0
                        || !context.reachesWithGravityDrive(origin, tile, type)) {
                    continue;
                }
                List<UnitMove> moves = List.of(new UnitMove(origin.getPosition(), BoardView.SPACE, type, 1));
                Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, moves);
                if (legal.isEmpty()) continue;
                double gain = context.objectiveGain(tile, legal.get(), List.of()) + frontier;
                if (gain <= 0) continue;
                double score = gain
                        - DISTANCE_COST * context.distance(origin, tile, type)
                        - SHIP_COST_WEIGHT * fleetCost(seat, legal.get())
                        - (leavesHomeWithoutShips(context, origin, legal.get()) ? LAST_HOME_SHIP_COST : 0);
                if (best == null || score > best.score()) {
                    best = new TacticalPlan(Kind.POSITION, tile.getPosition(), legal.get(), Map.of(), score);
                }
            }
            Optional<TacticalPlan> group = groupPosition(context, tile, origin);
            if (group.isPresent() && (best == null || group.get().score() > best.score())) best = group.get();
        }
        return Optional.ofNullable(best);
    }

    private static double frontierExplore(Player seat, Tile tile) {
        if (!seat.hasTech(DARK_ENERGY_TAP) || BoardView.hasOwnShips(seat, tile)) return 0;
        boolean frontier = BoardView.space(tile).getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER));
        return frontier ? FRONTIER_CARD_VALUE + FRONTIER_POSITION_VALUE : 0;
    }

    private static Optional<TacticalPlan> groupPosition(Context context, Tile tile, Tile origin) {
        Player seat = context.seat;
        List<UnitMove> ships = new ArrayList<>();
        UnitHolder originSpace = BoardView.space(origin);
        for (UnitType type : POSITION_SHIPS) {
            int available = BoardView.undamaged(originSpace, seat, type);
            if (available > 0 && context.reaches(origin, tile, type)) {
                ships.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, available));
            }
        }
        gravityDriveShip(
                        context,
                        origin,
                        tile,
                        POSITION_SHIPS,
                        type -> BoardView.undamaged(originSpace, seat, type),
                        Comparator.comparingDouble((UnitType type) -> -cost(seat, type)))
                .ifPresent(ships::add);
        if (origin.isHomeSystem(context.game) && !ships.isEmpty()) keepOneShipHome(seat, origin, ships);
        if (movedNonFighterShips(ships) < 2) return Optional.empty();
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, ships);
        if (legal.isEmpty()) return Optional.empty();
        double gain = context.objectiveGain(tile, legal.get(), List.of()) + frontierExplore(seat, tile);
        if (gain <= 0) return Optional.empty();
        int farthest = legal.get().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(move -> context.distance(origin, tile, move.type()))
                .max()
                .orElse(0);
        double score = gain - DISTANCE_COST * farthest - GROUP_MOVE_COST * movedNonFighterShips(legal.get());
        return Optional.of(new TacticalPlan(Kind.POSITION, tile.getPosition(), legal.get(), Map.of(), score));
    }

    private static Optional<List<UnitMove>> legalMoves(
            Context context, Tile origin, Tile target, List<UnitMove> moves) {
        Player seat = context.seat;
        int fleetAfterMove = BoardView.nonFighterShips(BoardView.space(target), seat) + movedNonFighterShips(moves);
        if (fleetAfterMove > seat.getFleetCC()) return Optional.empty();
        List<UnitMove> legal = new ArrayList<>(moves);
        UnitHolder space = BoardView.space(origin);
        int spare = capacityOf(seat, shipsIn(legal)) - cargoIn(legal);
        for (UnitType type : GROUND_FORCE_ORDER) {
            int carried = Math.min(spare, leftInSpace(seat, space, legal, type));
            if (carried > 0) {
                legal.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, carried));
                spare -= carried;
            }
        }
        int fighters = BoardView.count(space, seat, UnitType.Fighter);
        int strandedFighters = stranded(context, origin, legal);
        if (fighters > 0 && spare > 0 && strandedFighters > 0) {
            int carried = Math.min(spare, Math.min(fighters, strandedFighters));
            legal.add(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, carried));
        }
        return stranded(context, origin, legal) > 0 ? Optional.empty() : Optional.of(merge(legal));
    }

    private static int leftInSpace(Player seat, UnitHolder space, List<UnitMove> moves, UnitType type) {
        int moved = moves.stream()
                .filter(move -> BoardView.SPACE.equals(move.holder()) && move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
        return BoardView.count(space, seat, type) - moved;
    }

    private static int stranded(Context context, Tile origin, List<UnitMove> moves) {
        Player seat = context.seat;
        UnitHolder space = BoardView.space(origin);
        Map<UnitType, Integer> staying = new EnumMap<>(UnitType.class);
        staying.putAll(BoardView.ships(space, seat));
        for (UnitMove move : moves) {
            if (BoardView.SPACE.equals(move.holder()) && staying.containsKey(move.type())) {
                staying.merge(move.type(), -move.count(), Integer::sum);
            }
        }
        int capacity = staying.entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(entry -> Math.max(0, entry.getValue()) * BoardView.capacity(seat, entry.getKey()))
                .sum();
        int fighters = Math.max(0, staying.getOrDefault(UnitType.Fighter, 0));
        int allowance = hasDock(seat, origin) ? BoardView.DOCK_FIGHTER_ALLOWANCE : 0;
        int ground = BoardView.groundForces(space, seat) - groundForcesFromSpace(moves);
        return ground + Math.max(0, fighters - allowance) - capacity;
    }

    private static boolean hasDock(Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0);
    }

    private static List<UnitMove> merge(List<UnitMove> moves) {
        Map<String, UnitMove> merged = new LinkedHashMap<>();
        for (UnitMove move : moves) {
            merged.merge(
                    move.origin() + "|" + move.holder() + "|" + move.type(),
                    move,
                    (a, b) -> new UnitMove(a.origin(), a.holder(), a.type(), a.count() + b.count()));
        }
        return new ArrayList<>(merged.values());
    }

    private static List<UnitMove> shipsIn(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .toList();
    }

    private static int movedNonFighterShips(List<UnitMove> moves) {
        return shipsIn(moves).stream().mapToInt(UnitMove::count).sum();
    }

    private static int cargoIn(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.GROUND_FORCES.contains(move.type()) || move.type() == UnitType.Fighter)
                .mapToInt(UnitMove::count)
                .sum();
    }

    private static int groundForcesFromSpace(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.SPACE.equals(move.holder()))
                .filter(move -> BoardView.GROUND_FORCES.contains(move.type()))
                .mapToInt(UnitMove::count)
                .sum();
    }

    private static int capacityOf(Player seat, List<UnitMove> moves) {
        return moves.stream()
                .mapToInt(move -> move.count() * BoardView.capacity(seat, move.type()))
                .sum();
    }

    @Nullable
    private static Player singleOpponent(Game game, Player seat, Tile tile) {
        List<Player> others = game.getPlayers().values().stream()
                .filter(other -> other != seat)
                .filter(other -> other.getColor() != null)
                .filter(other -> tile.containsPlayersUnits(other)
                        || tile.getPlanetUnitHolders().stream()
                                .anyMatch(planet -> other.getPlanets().contains(planet.getName())))
                .toList();
        if (others.size() != 1 || !others.getFirst().isRealPlayer()) return null;
        return others.getFirst();
    }

    private static List<Planet> enemyPlanets(Game game, Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .filter(planet -> BoardView.controller(game, planet.getName()) != null
                        || BoardView.enemyGroundForcesOn(game, seat, planet))
                .sorted(Comparator.comparingDouble(BoardView::planetValue).reversed())
                .toList();
    }

    private static boolean canStealTech(Game game, Player seat, Player opponent) {
        return seat.hasAbility("technological_singularity")
                && !ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(
                                seat, opponent, new ArrayList<>(), game)
                        .isEmpty();
    }

    static int movableGroundForces(Game game, Player seat, Tile origin) {
        return unitCount(availableGroundForces(game, seat, origin));
    }

    private static List<UnitMove> availableGroundForces(Game game, Player seat, Tile origin) {
        List<UnitMove> infantry = new ArrayList<>();
        List<UnitMove> mechs = new ArrayList<>();
        String position = origin.getPosition();
        UnitHolder space = BoardView.space(origin);
        addMove(
                infantry,
                position,
                BoardView.SPACE,
                UnitType.Infantry,
                BoardView.count(space, seat, UnitType.Infantry));
        addMove(mechs, position, BoardView.SPACE, UnitType.Mech, BoardView.count(space, seat, UnitType.Mech));
        for (Planet planet : origin.getPlanetUnitHolders()) {
            if (!seat.getPlanets().contains(planet.getName())) continue;
            int planetMechs = BoardView.count(planet, seat, UnitType.Mech);
            int garrison = garrison(game, origin, planet);
            int mechsStaying = Math.min(planetMechs, garrison);
            int infantryStaying = garrison - mechsStaying;
            int planetInfantry = BoardView.count(planet, seat, UnitType.Infantry);
            addMove(infantry, position, planet.getName(), UnitType.Infantry, planetInfantry - infantryStaying);
            addMove(mechs, position, planet.getName(), UnitType.Mech, planetMechs - mechsStaying);
        }
        infantry.addAll(mechs);
        return infantry;
    }

    private static void addMove(List<UnitMove> moves, String origin, String holder, UnitType type, int count) {
        if (count > 0) moves.add(new UnitMove(origin, holder, type, count));
    }

    private static int garrison(Game game, Tile origin, Planet planet) {
        if (origin.isHomeSystem(game)) return HOME_GARRISON;
        return game.mecatols().contains(planet.getName()) ? MECATOL_GARRISON : 0;
    }

    private static int unitCount(List<UnitMove> moves) {
        return moves.stream().mapToInt(UnitMove::count).sum();
    }

    private static List<UnitMove> take(List<UnitMove> available, int wanted) {
        List<UnitMove> taken = new ArrayList<>();
        int remaining = wanted;
        for (UnitMove move : available) {
            if (remaining <= 0) break;
            int count = Math.min(remaining, move.count());
            taken.add(new UnitMove(move.origin(), move.holder(), move.type(), count));
            remaining -= count;
        }
        return taken;
    }

    private static List<UnitMove> skip(List<UnitMove> available, int skipped) {
        List<UnitMove> rest = new ArrayList<>();
        int toSkip = skipped;
        for (UnitMove move : available) {
            int skippedHere = Math.min(toSkip, move.count());
            toSkip -= skippedHere;
            addMove(rest, move.origin(), move.holder(), move.type(), move.count() - skippedHere);
        }
        return rest;
    }

    private static void keepOneShipHome(Player seat, Tile home, List<UnitMove> ships) {
        if (BoardView.nonFighterShips(BoardView.space(home), seat) > movedNonFighterShips(ships)) return;
        UnitMove guard = ships.stream()
                .min(Comparator.comparingInt((UnitMove move) -> BoardView.capacity(seat, move.type()))
                        .thenComparingDouble(move -> BoardView.model(seat, move.type())
                                .map(UnitModel::getCost)
                                .orElse(0f)))
                .orElseThrow();
        int index = ships.indexOf(guard);
        if (guard.count() > 1) {
            ships.set(index, new UnitMove(guard.origin(), guard.holder(), guard.type(), guard.count() - 1));
        } else {
            ships.remove(index);
        }
    }

    private static boolean leavesHomeWithoutShips(Context context, Tile origin, List<UnitMove> moves) {
        if (!origin.isHomeSystem(context.game)) return false;
        return BoardView.nonFighterShips(BoardView.space(origin), context.seat) - movedNonFighterShips(moves) <= 0;
    }

    private static Map<UnitType, Integer> shipsOf(List<UnitMove> moves) {
        return unitsOf(moves.stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()) || move.type() == UnitType.Fighter)
                .toList());
    }

    private static Map<UnitType, Integer> unitsOf(List<UnitMove> moves) {
        Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);
        for (UnitMove move : moves) units.merge(move.type(), move.count(), Integer::sum);
        return units;
    }

    private static Map<UnitType, Integer> groundOn(UnitHolder planet, Player player) {
        Map<UnitType, Integer> ground = new HashMap<>();
        for (UnitType type : BoardView.GROUND_FORCES) ground.put(type, BoardView.count(planet, player, type));
        return ground;
    }

    private static double groundOdds(
            Game game,
            Tile tile,
            Planet planet,
            Player seat,
            Player opponent,
            Map<UnitType, Integer> sent,
            int bombardmentHits) {
        Side attacking = new Side(seat, sent);
        Side defending = new Side(opponent, groundOn(planet, opponent));
        Map<UnitType, Integer> defendersDamaged = damagedAmong(planet, opponent, defending.units());
        Force defence = CombatForces.force(
                opponent,
                afterCannonFire(
                        CombatForces.combatants(game, tile, planet, defending, attacking, defendersDamaged),
                        bombardmentHits),
                true);
        Force attack = CombatForces.withExpectedCopy(
                game,
                seat,
                opponent,
                CombatForces.force(
                        seat, CombatForces.combatants(game, tile, planet, attacking, defending, Map.of()), true),
                defence,
                true,
                true);
        return CombatOdds.resolve(attack, defence).attackerWins();
    }

    private static double fleetCost(Player seat, List<UnitMove> moves) {
        return moves.stream()
                .mapToDouble(move -> BoardView.model(seat, move.type())
                                .map(UnitModel::getCost)
                                .orElse(0f)
                        * move.count())
                .sum();
    }

    private static final class Context {
        private final Game game;
        private final Player seat;
        private final Map<String, Map<String, Integer>> reachCache = new HashMap<>();
        private final Map<String, Boolean> spaceCannonCache = new HashMap<>();
        private List<Tile> origins;
        private ObjectiveValue objectives;
        private Boolean canPayCustodians;
        private ExploreOutlook exploreOutlook;
        private final Map<String, Double> exploreValues = new HashMap<>();

        Context(Game game, Player seat) {
            this.game = game;
            this.seat = seat;
        }

        double exploreValue(Tile tile, Planet planet) {
            if (exploreOutlook == null) exploreOutlook = new ExploreOutlook(game, seat);
            return exploreValues.computeIfAbsent(
                    planet.getName(),
                    name -> ExploreDeck.expectedValueOfPlanet(
                            ExploreSite.landing(game, seat, tile, planet, 1, 0, exploreOutlook)));
        }

        ObjectiveValue objectives() {
            if (objectives == null) objectives = new ObjectiveValue(game, seat);
            return objectives;
        }

        double objectiveGain(Tile target, List<UnitMove> moves, Collection<String> gainedPlanets) {
            List<Footprint.Move> footprintMoves = moves.stream()
                    .map(move -> new Footprint.Move(move.origin(), move.type(), move.count()))
                    .toList();
            ObjectiveValue value = objectives();
            return value.gain(value.before().after(target.getPosition(), footprintMoves, gainedPlanets));
        }

        double builtGain(Tile tile, ProductionPlanner.BuildPlan build) {
            Map<UnitType, Integer> built = new EnumMap<>(UnitType.class);
            for (ProductionPlanner.BuildOrder order : build.orders())
                built.merge(order.type(), order.units(), Integer::sum);
            ObjectiveValue value = objectives();
            return value.gain(value.before().withBuilt(tile.getPosition(), built));
        }

        boolean canPayCustodians() {
            if (canPayCustodians == null) {
                canPayCustodians = ScoringReserve.planAfterReserve(game, seat, SpendCost.influence(CUSTODIANS_COST))
                        .isPresent();
            }
            return canPayCustodians;
        }

        List<Tile> origins() {
            if (origins == null) {
                origins = game.getTileMap().values().stream()
                        .filter(tile -> !tile.hasPlayerCC(seat))
                        .filter(tile -> BoardView.hasOwnShips(seat, tile))
                        .filter(tile -> !tile.isGravityRift(game, seat))
                        .toList();
            }
            return origins;
        }

        boolean reaches(Tile origin, Tile target, UnitType type) {
            return distance(origin, target, type) <= BoardView.moveValue(seat, type);
        }

        boolean reachesWithGravityDrive(Tile origin, Tile target, UnitType type) {
            return distance(origin, target, type) <= BoardView.moveValueWithGravityDrive(seat, type);
        }

        int distance(Tile origin, Tile target, UnitType type) {
            int move = BoardView.moveValueWithGravityDrive(seat, type);
            Map<String, Integer> reach = reachCache.computeIfAbsent(
                    origin.getPosition() + "#" + move,
                    key -> MovementGraph.reach(game, seat, origin.getPosition(), move));
            return reach.getOrDefault(target.getPosition(), Integer.MAX_VALUE);
        }

        boolean coveredByEnemySpaceCannon(Tile tile) {
            return spaceCannonCache.computeIfAbsent(
                    tile.getPosition(),
                    position -> ButtonHelper.getPlayersWithPds2Cover(seat, game, position).stream()
                            .anyMatch(player -> player != seat));
        }
    }
}
