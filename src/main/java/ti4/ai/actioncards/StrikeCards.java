package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.eval.BoardView;
import ti4.ai.strategy.CardFacade;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class StrikeCards {

    static final String CRIPPLE_DEFENSES = "cripple";
    static final String PLAGUE = "plague";
    static final String UNSTABLE_PLANET = "unstable";
    static final String REACTOR_MELTDOWN = "meltdown";
    static final String TACTICAL_BOMBARDMENT = "tactical";
    static final String LUCKY_SHOT = "lucky";
    private static final String SEPARATOR = "_";
    private static final String CRIPPLE_FACTION = "crippleStep2_";
    private static final String CRIPPLE_PLANET = "crippleStep3_";
    private static final String PLAGUE_FACTION = "plagueStep2_";
    private static final String PLAGUE_PLANET = "plagueStep3_";
    private static final String UNSTABLE_FACTION = "unstableStep2_";
    private static final String UNSTABLE_PLANET_STEP = "unstableStep3_";
    private static final String MELTDOWN_FACTION = "reactorMeltdownStep2_";
    private static final String MELTDOWN_DOCK = "reactorMeltdownStep3_";
    private static final String BOMBARDMENT_SYSTEM = "tacticalBombardmentStep2_";
    private static final String LUCKY_SYSTEM = "luckyShotIn_";
    private static final String LUCKY_SHIP = "luckyShotOn_";
    private static final String DAMAGED = "damaged";
    private static final String SPACE = "space";
    private static final double PDS_VALUE = 1.0;
    private static final double INFANTRY_VALUE = 0.5;
    private static final double PLAGUE_KILL_CHANCE = 0.5;
    private static final int UNSTABLE_KILLS = 3;
    private static final double EXHAUST_SHARE = 0.5;
    private static final double SHIP_COST_SHARE = 1.0;

    static AiCard crippleDefenses() {
        return AiActionCards.component(
                CRIPPLE_DEFENSES,
                StrikeCards::cripplePlan,
                "resolveCrippleDefensesStep1",
                List.of(CRIPPLE_FACTION, CRIPPLE_PLANET),
                CardChains.chain(
                        CardChains.next(
                                CRIPPLE_FACTION,
                                Source.MENTIONED,
                                (context, faction) -> otherPlayerValue(context.game(), context.seat(), faction),
                                "choose the player:"),
                        CardChains.last(
                                CRIPPLE_PLANET,
                                Source.MENTIONED,
                                (context, suffix) -> crippleValue(context.game(), context.seat(), suffix),
                                "cripple")));
    }

    static AiCard plague() {
        return AiActionCards.component(
                PLAGUE,
                StrikeCards::plaguePlan,
                "resolvePlagueStep1",
                List.of(PLAGUE_FACTION, PLAGUE_PLANET),
                CardChains.chain(
                        CardChains.next(
                                PLAGUE_FACTION,
                                Source.MENTIONED,
                                (context, faction) -> otherPlayerValue(context.game(), context.seat(), faction),
                                "choose the player:"),
                        CardChains.last(
                                PLAGUE_PLANET,
                                Source.MENTIONED,
                                (context, suffix) -> plagueValue(context.game(), context.seat(), suffix),
                                "send the plague to")));
    }

    static AiCard unstablePlanet() {
        return AiActionCards.component(
                UNSTABLE_PLANET,
                StrikeCards::unstablePlan,
                "resolveUnstableStep1",
                List.of(UNSTABLE_FACTION, UNSTABLE_PLANET_STEP),
                CardChains.chain(
                        CardChains.next(
                                UNSTABLE_FACTION,
                                Source.MENTIONED,
                                (context, faction) -> otherPlayerValue(context.game(), context.seat(), faction),
                                "choose the player:"),
                        CardChains.last(
                                UNSTABLE_PLANET_STEP,
                                Source.MENTIONED,
                                (context, suffix) -> unstableValue(context.game(), context.seat(), suffix),
                                "destabilise")));
    }

    static AiCard reactorMeltdown() {
        return AiActionCards.component(
                REACTOR_MELTDOWN,
                StrikeCards::meltdownPlan,
                "resolveReactorMeltdownStep1",
                List.of(MELTDOWN_FACTION, MELTDOWN_DOCK),
                CardChains.chain(
                        CardChains.next(
                                MELTDOWN_FACTION,
                                Source.MENTIONED,
                                (context, faction) -> otherPlayerValue(context.game(), context.seat(), faction),
                                "choose the player:"),
                        CardChains.last(
                                MELTDOWN_DOCK,
                                Source.MENTIONED,
                                (context, suffix) -> meltdownValue(context.game(), context.seat(), suffix),
                                "melt the dock in")));
    }

    static AiCard tacticalBombardment() {
        return AiActionCards.component(
                TACTICAL_BOMBARDMENT,
                StrikeCards::bombardmentPlan,
                "resolveTacticalBombardmentStep1",
                List.of(BOMBARDMENT_SYSTEM),
                CardChains.chain(CardChains.last(
                        BOMBARDMENT_SYSTEM,
                        Source.MENTIONED,
                        (context, position) -> bombardmentValue(context.game(), context.seat(), position),
                        "bombard")));
    }

    static AiCard luckyShot() {
        return AiActionCards.component(
                LUCKY_SHOT,
                StrikeCards::luckyShotPlan,
                "startToLuckyShotAUnit_0",
                List.of(LUCKY_SYSTEM, LUCKY_SHIP),
                CardChains.chain(
                        CardChains.next(
                                LUCKY_SYSTEM,
                                Source.OWNED,
                                (context, position) -> luckySystemValue(context.game(), context.seat(), position),
                                "aim at"),
                        CardChains.last(
                                LUCKY_SHIP,
                                Source.OWNED,
                                (context, suffix) -> luckyShipValue(context.game(), context.seat(), suffix),
                                "shoot")));
    }

    private static List<Player> rivals(Game game, Player seat) {
        return game.getRealPlayers().stream().filter(other -> other != seat).toList();
    }

    private static double otherPlayerValue(Game game, Player seat, String faction) {
        Player other = game.getPlayerFromColorOrFaction(faction);
        return other == null || other == seat ? Double.NEGATIVE_INFINITY : other.getTotalVictoryPoints();
    }

    private static String factionOf(String suffix) {
        return suffix.contains(SEPARATOR) ? suffix.substring(0, suffix.indexOf(SEPARATOR)) : suffix;
    }

    private static String restOf(String suffix) {
        return suffix.contains(SEPARATOR) ? suffix.substring(suffix.indexOf(SEPARATOR) + 1) : "";
    }

    private record Target(Player owner, String planet, double value) {}

    private static Optional<Plan> bestPlanet(
            Game game, Player seat, ToDoubleFunction<Target> value, String verb, String announce) {
        if (game.isFowMode()) return Optional.empty();
        return rivals(game, seat).stream()
                .flatMap(owner -> owner.getPlanets().stream().map(planet -> new Target(owner, planet, 0)))
                .map(target -> new Target(target.owner(), target.planet(), value.applyAsDouble(target)))
                .filter(target -> target.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Target::value)
                        .thenComparing(target -> target.owner().getFaction(), Comparator.reverseOrder())
                        .thenComparing(Target::planet, Comparator.reverseOrder()))
                .map(target -> Plan.aimedAt(
                        target.value(),
                        CardChains.target(
                                target.owner().getFaction(), target.owner().getFaction() + SEPARATOR + target.planet()),
                        verb + target.planet() + announce));
    }

    private static UnitHolder holder(Game game, String planet) {
        return game.getUnitHolderFromPlanet(planet);
    }

    private static int pdsOn(Game game, String planet) {
        UnitHolder holder = holder(game, planet);
        if (holder == null) return 0;
        return game.getRealPlayers().stream()
                .mapToInt(player -> BoardView.count(holder, player, UnitType.Pds))
                .sum();
    }

    private static boolean holdsUnits(Game game, Player seat, String planet, UnitType type) {
        return BoardView.count(holder(game, planet), seat, type) > 0;
    }

    static Optional<Plan> cripplePlan(Game game, Player seat) {
        return bestPlanet(game, seat, target -> crippleTarget(game, seat, target.owner(), target.planet()), "on ", ".");
    }

    private static double crippleValue(Game game, Player seat, String suffix) {
        Player owner = game.getPlayerFromColorOrFaction(factionOf(suffix));
        if (!suffix.contains(SEPARATOR)) return otherPlayerValue(game, seat, suffix);
        return owner == null ? Double.NEGATIVE_INFINITY : crippleTarget(game, seat, owner, restOf(suffix));
    }

    private static double crippleTarget(Game game, Player seat, Player owner, String planet) {
        if (owner == seat || !owner.getPlanets().contains(planet) || holdsUnits(game, seat, planet, UnitType.Pds)) {
            return Double.NEGATIVE_INFINITY;
        }
        return PDS_VALUE * pdsOn(game, planet) * TableStanding.of(game, seat, owner);
    }

    static Optional<Plan> plaguePlan(Game game, Player seat) {
        return bestPlanet(game, seat, target -> plagueTarget(game, seat, target.owner(), target.planet()), "on ", ".");
    }

    private static double plagueValue(Game game, Player seat, String suffix) {
        if (!suffix.contains(SEPARATOR)) return otherPlayerValue(game, seat, suffix);
        Player owner = game.getPlayerFromColorOrFaction(factionOf(suffix));
        return owner == null ? Double.NEGATIVE_INFINITY : plagueTarget(game, seat, owner, restOf(suffix));
    }

    private static double plagueTarget(Game game, Player seat, Player owner, String planet) {
        if (owner == seat || !owner.getPlanets().contains(planet) || seatHasGroundForcesOn(game, seat, planet)) {
            return Double.NEGATIVE_INFINITY;
        }
        UnitHolder holder = holder(game, planet);
        int infantry = game.getRealPlayers().stream()
                .mapToInt(player -> BoardView.count(holder, player, UnitType.Infantry))
                .sum();
        return PLAGUE_KILL_CHANCE * infantry * INFANTRY_VALUE * TableStanding.of(game, seat, owner);
    }

    private static boolean seatHasGroundForcesOn(Game game, Player seat, String planet) {
        UnitHolder holder = holder(game, planet);
        return holder != null && BoardView.groundForces(holder, seat) > 0;
    }

    static Optional<Plan> unstablePlan(Game game, Player seat) {
        return bestPlanet(
                game, seat, target -> unstableTarget(game, seat, target.owner(), target.planet()), "on ", ".");
    }

    private static double unstableValue(Game game, Player seat, String suffix) {
        if (!suffix.contains(SEPARATOR)) return otherPlayerValue(game, seat, suffix);
        Player owner = game.getPlayerFromColorOrFaction(factionOf(suffix));
        return owner == null ? Double.NEGATIVE_INFINITY : unstableTarget(game, seat, owner, restOf(suffix));
    }

    private static double unstableTarget(Game game, Player seat, Player owner, String planet) {
        if (owner == seat
                || !owner.getPlanets().contains(planet)
                || !ButtonHelper.getTypeOfPlanet(game, planet).contains("hazardous")
                || seatHasGroundForcesOn(game, seat, planet)) {
            return Double.NEGATIVE_INFINITY;
        }
        int infantry = Math.min(UNSTABLE_KILLS, BoardView.count(holder(game, planet), owner, UnitType.Infantry));
        Planet info = game.getPlanetsInfo().get(planet);
        double exhausted = owner.getReadiedPlanets().contains(planet) && info != null
                ? EXHAUST_SHARE * BoardView.planetValue(info)
                : 0;
        return (exhausted + infantry * INFANTRY_VALUE) * TableStanding.of(game, seat, owner);
    }

    static Optional<Plan> meltdownPlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return rivals(game, seat).stream()
                .flatMap(owner ->
                        docks(game, owner).stream().map(dock -> new Dock(owner, dock.position(), dock.holder())))
                .max(Comparator.comparingDouble((Dock dock) -> meltdownTarget(game, seat, dock))
                        .thenComparing(dock -> dock.owner().getFaction(), Comparator.reverseOrder())
                        .thenComparing(Dock::position, Comparator.reverseOrder())
                        .thenComparing(Dock::holder, Comparator.reverseOrder()))
                .map(dock -> Plan.aimedAt(
                        meltdownTarget(game, seat, dock),
                        CardChains.target(
                                dock.owner().getFaction(),
                                dock.owner().getFaction() + SEPARATOR + dock.position() + SEPARATOR + dock.holder()),
                        "on the dock in " + dock.position() + "."));
    }

    private record Dock(Player owner, String position, String holder) {}

    private record Spot(String position, String holder) {}

    private static List<Spot> docks(Game game, Player owner) {
        List<Spot> spots = new java.util.ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isHomeSystem(game)) continue;
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                boolean custodia = holder.getName().contains("custodia")
                        || holder.getName().contains("ghoti");
                if (!custodia && BoardView.count(holder, owner, UnitType.Spacedock) > 0) {
                    spots.add(new Spot(tile.getPosition(), holder.getName()));
                }
            }
        }
        return spots;
    }

    private static double meltdownValue(Game game, Player seat, String suffix) {
        if (!suffix.contains(SEPARATOR)) return otherPlayerValue(game, seat, suffix);
        Player owner = game.getPlayerFromColorOrFaction(factionOf(suffix));
        String rest = restOf(suffix);
        int cut = rest.indexOf(SEPARATOR);
        if (owner == null || cut < 0) return Double.NEGATIVE_INFINITY;
        return meltdownTarget(game, seat, new Dock(owner, rest.substring(0, cut), rest.substring(cut + 1)));
    }

    private static double meltdownTarget(Game game, Player seat, Dock dock) {
        Tile tile = game.getTileByPosition(dock.position());
        if (dock.owner() == seat || tile == null || tile.isHomeSystem(game)) return Double.NEGATIVE_INFINITY;
        UnitHolder holder = tile.getUnitHolders().get(dock.holder());
        if (holder == null || BoardView.count(holder, dock.owner(), UnitType.Spacedock) == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return CardFacade.dockValue(game) * TableStanding.of(game, seat, dock.owner());
    }

    static Optional<Plan> bombardmentPlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return ButtonHelper.getTilesOfUnitsWithBombard(seat, game).stream()
                .map(tile -> Plan.aimedAt(
                        bombardmentValue(game, seat, tile.getPosition()),
                        tile.getPosition(),
                        "in " + tile.getRepresentation() + "."))
                .filter(plan -> plan.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target, Comparator.reverseOrder()));
    }

    private static double bombardmentValue(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return Double.NEGATIVE_INFINITY;
        double value = 0;
        boolean any = false;
        for (Player owner : rivals(game, seat)) {
            for (Planet planet : BoardView.planets(tile)) {
                if (!owner.getReadiedPlanets().contains(planet.getName())) continue;
                any = true;
                value += EXHAUST_SHARE * BoardView.planetValue(planet) * TableStanding.of(game, seat, owner);
            }
        }
        return any ? value : Double.NEGATIVE_INFINITY;
    }

    static Optional<Plan> luckyShotPlan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return game.getTileMap().values().stream()
                .map(tile -> Plan.aimedAt(
                        luckySystemValue(game, seat, tile.getPosition()),
                        tile.getPosition(),
                        "in " + tile.getRepresentation() + "."))
                .filter(plan -> plan.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target, Comparator.reverseOrder()));
    }

    private static double luckySystemValue(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || BoardView.planets(tile).stream()
                        .noneMatch(planet -> seat.getPlanets().contains(planet.getName()))) {
            return Double.NEGATIVE_INFINITY;
        }
        double best = Double.NEGATIVE_INFINITY;
        UnitHolder space = BoardView.space(tile);
        for (Player owner : rivals(game, seat)) {
            for (UnitType type : List.of(UnitType.Dreadnought, UnitType.Cruiser, UnitType.Destroyer)) {
                if (BoardView.count(space, owner, type) > 0) best = Math.max(best, shipValue(game, seat, owner, type));
            }
        }
        return best;
    }

    private static double shipValue(Game game, Player seat, Player owner, UnitType type) {
        UnitModel model = BoardView.model(owner, type).orElse(null);
        double cost = model == null ? 1 : model.getCost();
        return SHIP_COST_SHARE * cost * TableStanding.of(game, seat, owner);
    }

    private static double luckyShipValue(Game game, Player seat, String suffix) {
        String[] fields = suffix.split(SEPARATOR);
        if (fields.length < 3) return Double.NEGATIVE_INFINITY;
        Player owner = game.getPlayerFromColorOrFaction(fields[fields.length - 1]);
        UnitType type = typeOf(fields[1].replace(DAMAGED, ""), owner);
        if (owner == null || owner == seat || type == null) return Double.NEGATIVE_INFINITY;
        return shipValue(game, seat, owner, type) + (fields[1].contains(DAMAGED) ? 0.5 : 0);
    }

    private static UnitType typeOf(String unitName, Player owner) {
        if (owner == null) return null;
        for (UnitType type : List.of(UnitType.Dreadnought, UnitType.Cruiser, UnitType.Destroyer)) {
            UnitKey key = new UnitKey(type, owner.getColor());
            if (key.unitName().equals(unitName)) return type;
        }
        return null;
    }
}
