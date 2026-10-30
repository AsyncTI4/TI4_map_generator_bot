package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.NeutralSetups;
import ti4.ai.tactical.NeutralSetups.Pirates;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.thundersedge.TeHelperActionCards;

@UtilityClass
class NeutralUnitCards {

    static final String PIRATE_CONTRACT = "piratecontract1";
    static final String PIRATE_FLEET = "piratefleet";
    static final String MERCENARY_CONTRACT = "mercenarycontract";
    static final String PIRATE_CONTRACT_COPY = "piratecontract[1-4]";
    static final int MERCENARY_TRADE_GOODS = 2;
    static final int PIRATE_FLEET_RESOURCES = 3;
    static final int FIRST_PAGE_PLANETS = 22;
    private static final String CONTRACT_SYSTEM_PREFIX = "resolvePirateContract_";
    private static final String FLEET_SYSTEM_PREFIX = "resolvePirateFleet_";
    private static final String MERCENARY_PLANET_PREFIX = "resolveTeMercenaryContract_";
    private static final String CONTRACT = "resolvePirateContract";
    private static final String EXHAUST_DONE = "deleteButtons_spitItOut";
    private static final String FLEET = "resolvePirateFleet";
    private static final double BLOCK_SHARE = 0.5;
    private static final double BLOCK_CAP = 3.0;
    private static final double FLEET_BLOCK = 2.0;
    private static final double PLANET_SHARE = 0.3;

    static AiCard pirateContract() {
        return AiActionCards.componentOfCopies(
                PIRATE_CONTRACT,
                alias -> alias.matches(PIRATE_CONTRACT_COPY),
                Evaluator.atMoment(NeutralUnitCards::pirateContractPlan),
                "pirateContract",
                List.of(CONTRACT_SYSTEM_PREFIX),
                CardChains.chain(CardChains.last(
                        CONTRACT_SYSTEM_PREFIX,
                        Source.OWNED,
                        (context, position) ->
                                contractValue(context.game(), context.seat(), position, PlayMoment.IN_TURN),
                        "post the pirates in")));
    }

    static AiCard pirateFleet() {
        return AiActionCards.componentWithResolveHook(
                PIRATE_FLEET,
                Evaluator.atMoment(NeutralUnitCards::pirateFleetPlan),
                "pirateFleet",
                List.of(FLEET_SYSTEM_PREFIX),
                CardChains.chain(CardChains.last(
                        FLEET_SYSTEM_PREFIX,
                        Source.OWNED,
                        (context, position) -> fleetValue(context.game(), context.seat(), position, PlayMoment.IN_TURN),
                        "post the pirate fleet in")),
                NeutralUnitCards::expectPayment);
    }

    private static void expectPayment(AiTurnContext context) {
        paymentFor(context.game(), context.seat())
                .ifPresent(payment -> PaymentRules.expect(context, "Pirate Fleet", payment, EXHAUST_DONE));
    }

    private static Optional<Wallet.Payment> paymentFor(Game game, Player seat) {
        SpendCost cost = SpendCost.resources(PIRATE_FLEET_RESOURCES);
        return ScoringReserve.planAfterReserve(game, seat, cost)
                .or(() -> Wallet.of(game, seat).plan(cost));
    }

    static AiCard mercenaryContract() {
        return AiActionCards.component(
                MERCENARY_CONTRACT,
                NeutralUnitCards::mercenaryPlan,
                "teMercenaryContract_page0",
                List.of(MERCENARY_PLANET_PREFIX),
                CardChains.chain(CardChains.last(
                        MERCENARY_PLANET_PREFIX,
                        Source.OWNED,
                        (context, planet) -> mercenaryValue(context.game(), context.seat(), planet),
                        "post the mercenaries on")));
    }

    static Optional<Plan> pirateContractPlan(Game game, Player seat, PlayMoment moment) {
        if (game.isFowMode()) return Optional.empty();
        return legalSystems(game, seat, CONTRACT).stream()
                .map(tile -> Plan.aimedAt(
                        contractValue(game, seat, tile.getPosition(), moment),
                        tile.getPosition(),
                        "in " + tile.getRepresentation() + "."))
                .filter(plan -> plan.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target, Comparator.reverseOrder()));
    }

    static Optional<Plan> pirateFleetPlan(Game game, Player seat, PlayMoment moment) {
        if (game.isFowMode()
                || ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(PIRATE_FLEET_RESOURCES))
                        .isEmpty()) {
            return Optional.empty();
        }
        return legalSystems(game, seat, FLEET).stream()
                .map(tile -> Plan.aimedAt(
                        fleetValue(game, seat, tile.getPosition(), moment),
                        tile.getPosition(),
                        "in " + tile.getRepresentation() + "."))
                .filter(plan -> plan.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target, Comparator.reverseOrder()));
    }

    static Optional<Plan> mercenaryPlan(Game game, Player seat) {
        if (game.isFowMode()
                || ScoringReserve.planAfterReserve(game, seat, SpendCost.tradeGoods(MERCENARY_TRADE_GOODS))
                        .isEmpty()) {
            return Optional.empty();
        }
        return firstPageCandidates(game, seat).stream()
                .map(planet -> Plan.aimedAt(
                        mercenaryValue(game, seat, planet.getName()), planet.getName(), "on " + planet.getName() + "."))
                .filter(plan -> plan.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target, Comparator.reverseOrder()));
    }

    private static List<Tile> legalSystems(Game game, Player seat, String prefix) {
        return game.getTileMap().values().stream()
                .filter(tile -> TeHelperActionCards.legalPirateTarget(game, tile, prefix))
                .toList();
    }

    private static double contractValue(Game game, Player seat, String position, PlayMoment moment) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !TeHelperActionCards.legalPirateTarget(game, tile, CONTRACT)) {
            return Double.NEGATIVE_INFINITY;
        }
        double blocking = onOwnRoutes(game, seat, tile)
                ? Double.NEGATIVE_INFINITY
                : Math.min(BLOCK_CAP, blockedRivals(game, seat, tile, false));
        double setup = setupValue(game, seat, tile, Pirates.CONTRACT, moment);
        double dust = DustTarget.value(game, seat, tile);
        double best = Math.max(setup, dust);
        return best > 0 ? Math.max(blocking, best) : blocking;
    }

    private static double fleetValue(Game game, Player seat, String position, PlayMoment moment) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !TeHelperActionCards.legalPirateTarget(game, tile, FLEET)) {
            return Double.NEGATIVE_INFINITY;
        }
        double denial = onOwnRoutes(game, seat, tile) ? 0 : FLEET_BLOCK * contenderPresence(game, seat, tile);
        double blocking = denial > 0 ? denial - PIRATE_FLEET_RESOURCES : Double.NEGATIVE_INFINITY;
        double setup = setupValue(game, seat, tile, Pirates.FLEET, moment);
        return setup > 0 ? Math.max(blocking, setup - PIRATE_FLEET_RESOURCES) : blocking;
    }

    private static double setupValue(Game game, Player seat, Tile tile, Pirates pirates, PlayMoment moment) {
        return moment == PlayMoment.BEFORE_PASSING ? 0 : NeutralSetups.value(game, seat, tile, pirates);
    }

    private static boolean onOwnRoutes(Game game, Player seat, Tile tile) {
        if (hasOwnPresence(seat, tile)) return true;
        return FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacent -> adjacent != null && hasOwnPresence(seat, adjacent));
    }

    private static boolean hasOwnPresence(Player seat, Tile tile) {
        return tile.containsPlayersUnits(seat)
                || BoardView.planets(tile).stream()
                        .anyMatch(planet -> seat.getPlanets().contains(planet.getName()));
    }

    private static double blockedRivals(Game game, Player seat, Tile tile, boolean contendersOnly) {
        double blocked = 0;
        for (Player rival : game.getRealPlayers()) {
            if (rival == seat || (contendersOnly && !ScoringStakes.contender(game, seat, rival))) continue;
            if (nearby(game, rival, tile)) blocked += BLOCK_SHARE * TableStanding.of(game, seat, rival);
        }
        return blocked;
    }

    private static double contenderPresence(Game game, Player seat, Tile tile) {
        return game.getRealPlayers().stream()
                        .filter(rival -> rival != seat && ScoringStakes.contender(game, seat, rival))
                        .anyMatch(rival -> nearby(game, rival, tile))
                ? TableStanding.CONTENDER
                : 0;
    }

    private static boolean nearby(Game game, Player rival, Tile tile) {
        if (hasOwnPresence(rival, tile)) return true;
        return FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), rival, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacent -> adjacent != null && hasOwnPresence(rival, adjacent));
    }

    private static List<Planet> firstPageCandidates(Game game, Player seat) {
        return game.getPlanetsInfo().values().stream()
                .filter(planet -> mayBeMercenaryTarget(game, seat, planet))
                .limit(FIRST_PAGE_PLANETS)
                .toList();
    }

    private static boolean mayBeMercenaryTarget(Game game, Player seat, Planet planet) {
        if (planet.isHomePlanet(game)
                || planet.hasUnits()
                || seat.getPlanetsAllianceMode().contains(planet.getName())) {
            return false;
        }
        if (game.getTileFromPlanet(planet.getName()) == null) return false;
        var holder = game.getUnitHolderFromPlanet(planet.getName());
        return holder != null
                && !holder.isSpaceStation(game)
                && !planet.getTokenList().contains("dmz")
                && !planet.getTokenList().contains("dmz_large");
    }

    private static double mercenaryValue(Game game, Player seat, String planet) {
        Player owner = game.getPlayers().values().stream()
                .filter(player -> player != seat && player.getPlanets().contains(planet))
                .findFirst()
                .orElse(null);
        if (owner == null || !ScoringStakes.contender(game, seat, owner)) return Double.NEGATIVE_INFINITY;
        ObjectiveValue objectives = ObjectiveValue.publicOnly(game, owner);
        double lost = Math.max(0, -objectives.gain(Footprint.of(game, owner).withoutPlanet(planet)));
        double points = lost / ObjectiveValue.VICTORY_POINT_VALUE;
        Planet info = game.getPlanetsInfo().get(planet);
        double planetValue = info == null ? 0 : PLANET_SHARE * BoardView.planetValue(info);
        return ScoringStakes.rivalStake(game, owner, points) + planetValue - MERCENARY_TRADE_GOODS;
    }
}
