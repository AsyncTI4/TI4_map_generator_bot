package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.CardPrompts.Offer;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.explore.ExploreValues;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class BoardCards {

    static final String FIGHTER_CONSCRIPTION = "f_conscription";
    static final String REFIT_TROOPS = "refit";
    private static final String REFIT_PREFIX = "mitoMechPlacement_";
    private static final String REFIT_REASON = "_refit";
    private static final String PLANETS = ",";
    private static final double FIGHTER_VALUE = 0.5;
    private static final int MAX_SWAPS = 2;
    private static final int UNCAPPED_MECHS = 2;

    static AiCard fighterConscription() {
        return AiActionCards.component(
                FIGHTER_CONSCRIPTION, BoardCards::fighterPlan, "fighterConscription", List.of(), null);
    }

    static AiCard refitTroops() {
        return AiActionCards.component(
                REFIT_TROOPS, BoardCards::refitPlan, "resolveRefitTroops", REFIT_PREFIX, BoardCards::refitStep);
    }

    static Optional<Plan> fighterPlan(Game game, Player seat) {
        long tiles = game.getTileMap().values().stream()
                .filter(tile -> fighterFits(game, seat, tile))
                .count();
        long fighters = Math.min(tiles, unitRoom(game, seat, UnitType.Fighter, Integer.MAX_VALUE));
        if (fighters == 0) return Optional.empty();
        return Optional.of(Plan.of(FIGHTER_VALUE * fighters));
    }

    private static boolean fighterFits(Game game, Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        boolean dock = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0);
        int capacity = BoardView.MOVING_SHIPS.stream()
                .mapToInt(type -> BoardView.count(space, seat, type) * BoardView.capacity(seat, type))
                .sum();
        if (capacity == 0 && !dock) return false;
        boolean blockaded = game.getRealPlayersNNeutral().stream()
                .anyMatch(other -> other != seat && FoWHelper.playerHasActualShipsInSystem(other, tile));
        int room = capacity
                + (dock ? BoardView.DOCK_FIGHTER_ALLOWANCE : 0)
                - BoardView.count(space, seat, UnitType.Fighter)
                - BoardView.groundForces(space, seat);
        return !blockaded && room > 0;
    }

    private static int unitRoom(Game game, Player seat, UnitType type, int uncapped) {
        String asyncId = BoardView.model(seat, type).map(UnitModel::getAsyncId).orElse(null);
        if (asyncId == null) return 0;
        int cap = seat.getUnitCap(asyncId);
        if (cap <= 0) return uncapped;
        return Math.max(0, cap - ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId));
    }

    static Optional<Plan> refitPlan(Game game, Player seat) {
        List<String> holders = swapHolders(game, seat);
        if (holders.isEmpty()) return Optional.empty();
        double gain = holders.size() * (ExploreValues.MECH - ExploreValues.INFANTRY_UNIT);
        return Optional.of(Plan.aimedAt(
                gain, String.join(PLANETS, holders), "replacing " + holders.size() + " of its infantry with mechs."));
    }

    private static List<String> swapHolders(Game game, Player seat) {
        int swaps = Math.min(MAX_SWAPS, unitRoom(game, seat, UnitType.Mech, UNCAPPED_MECHS));
        List<Planet> planets = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> BoardView.count(planet, seat, UnitType.Infantry) > 0)
                .sorted(Comparator.comparingDouble(BoardView::planetValue)
                        .reversed()
                        .thenComparing(Planet::getName))
                .toList();
        Map<String, Integer> taken = new HashMap<>();
        List<String> holders = new ArrayList<>();
        for (int swap = 0; swap < swaps; swap++) {
            Optional<Planet> next = planets.stream()
                    .filter(planet ->
                            taken.getOrDefault(planet.getName(), 0) < BoardView.count(planet, seat, UnitType.Infantry))
                    .min(Comparator.comparingInt(planet -> taken.getOrDefault(planet.getName(), 0)));
            if (next.isEmpty()) break;
            taken.merge(next.get().getName(), 1, Integer::sum);
            holders.add(next.get().getName());
        }
        return holders;
    }

    private static Optional<AiDecision> refitStep(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        List<String> planned = new ArrayList<>(List.of(StringUtils.split(play.extra(), PLANETS)));
        List<Offer> offers =
                CardPrompts.ownedOffers(context, play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS, REFIT_PREFIX);
        Optional<Offer> choice = offers.stream()
                .filter(offer -> offer.suffix().endsWith(REFIT_REASON))
                .filter(offer -> hasInfantryAt(game, seat, holderOf(offer)))
                .max(Comparator.comparingInt((Offer offer) -> planned.contains(holderOf(offer)) ? 1 : 0)
                        .thenComparing(offer -> holderOf(offer), Comparator.reverseOrder()));
        if (choice.isEmpty()) return Optional.empty();
        planned.remove(holderOf(choice.get()));
        Stage next = planned.isEmpty() ? Stage.DONE : Stage.TARGET;
        PlayMemory.store(context, key, play.at(next, context.now()).withExtra(String.join(PLANETS, planned)));
        return Optional.of(
                choice.get().match().press("replace an infantry on " + holderOf(choice.get()) + " with a mech"));
    }

    private static String holderOf(Offer offer) {
        String[] parts = offer.suffix().split("_");
        return parts.length > 1 ? parts[1] : "";
    }

    private static boolean hasInfantryAt(Game game, Player seat, String holder) {
        Planet planet = game.getPlanetsInfo().get(holder);
        return planet != null && BoardView.count(planet, seat, UnitType.Infantry) > 0;
    }
}
