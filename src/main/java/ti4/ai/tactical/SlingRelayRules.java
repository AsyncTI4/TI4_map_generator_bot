package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class SlingRelayRules {

    static final String SLING_KEY = "slingRelay|";
    private static final String SLING_RELAY = "sr";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String EXHAUST = "exhaustTech_sr";
    private static final String TILE_PREFIX = "produceOneUnitInTile_";
    private static final String TILE_SUFFIX = "_sling";
    private static final String PLACE_PREFIX = "placeOneNDone_dontskip_";
    private static final String DONE_PREFIX = "deleteButtons_";
    private static final String MENU = "menu";
    private static final String TILE = "tile";
    private static final String UNIT = "unit";
    private static final String DONE = "done";
    private static final String FIELD = "~";
    private static final double STALL_VALUE = 0.5;
    private static final double TRADE_GOOD_VALUE = 0.6;

    record Choice(String position, BuildOrder ship) {}

    public static Optional<AiDecision> start(AiTurnContext context, List<AiPrompt> thisTurn) {
        Player seat = context.seat();
        String key = SLING_KEY + context.turnKey();
        if (!seat.hasTechReady(SLING_RELAY) || context.memory().has(key)) return Optional.empty();
        Optional<Choice> choice = best(context.game(), seat);
        if (choice.isEmpty()) return Optional.empty();
        Optional<Match> component = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals);
        BuildOrder ship = choice.get().ship();
        String cost = String.valueOf((int) Math.ceil(ship.cost()));
        component.ifPresent(match ->
                context.memory().put(key, String.join(FIELD, MENU, choice.get().position(), ship.unitId(), cost)));
        return component.map(match ->
                match.press("produce a " + choice.get().ship().unitId() + " with Sling Relay instead of passing"));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        String key = SLING_KEY + context.turnKey();
        String[] state =
                StringUtils.splitPreserveAllTokens(context.memory().get(key).orElse(""), FIELD);
        if (state == null || state.length != 4 || !StringUtils.isNumeric(state[3])) return Optional.empty();
        String position = state[1];
        String unit = state[2];
        String cost = state[3];
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        return switch (state[0]) {
            case MENU ->
                advance(
                                context,
                                key,
                                String.join(FIELD, TILE, position, unit, cost),
                                Prompts.owned(visible, context.faction(), EXHAUST::equals))
                        .map(match -> match.press("exhaust Sling Relay"));
            case TILE ->
                advance(
                                context,
                                key,
                                String.join(FIELD, UNIT, position, unit, cost),
                                Prompts.unowned(visible, (TILE_PREFIX + position + TILE_SUFFIX)::equals))
                        .map(match -> match.press("produce in " + position));
            case UNIT -> placeShip(context, key, visible, position, unit, Integer.parseInt(cost));
            default -> Optional.empty();
        };
    }

    private static Optional<Match> advance(AiTurnContext context, String key, String next, Optional<Match> found) {
        found.ifPresent(match -> context.memory().put(key, next));
        return found;
    }

    private static Optional<AiDecision> placeShip(
            AiTurnContext context, String key, List<AiPrompt> visible, String position, String unit, int cost) {
        String handler = PLACE_PREFIX + unit + "_" + position;
        Optional<Match> place = Prompts.owned(visible, context.faction(), handler::equals);
        if (place.isEmpty()) return Optional.empty();
        context.memory().put(key, DONE);
        ScoringReserve.planAfterReserve(context.game(), context.seat(), SpendCost.resources(cost))
                .ifPresent(payment -> PaymentRules.expect(context, "Sling Relay", payment, DONE_PREFIX + handler));
        return Optional.of(place.get().press("produce a " + unit + " with Sling Relay"));
    }

    static Optional<Choice> best(Game game, Player seat) {
        int spendable = ProductionPlanner.spendableResources(game, seat);
        int planetResources = Optional.ofNullable(Helper.getPlayerResourcesAvailable(seat, game))
                .orElse(0);
        int freeResources = Math.min(spendable, planetResources);
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock).stream()
                .distinct()
                .flatMap(tile -> ProductionPlanner.oneShipOptions(game, seat, tile, spendable).stream()
                        .map(order -> new Choice(tile.getPosition(), order)))
                .filter(choice -> netValue(choice, freeResources) > 0)
                .max(Comparator.comparingDouble(choice -> netValue(choice, freeResources)));
    }

    private static double netValue(Choice choice, int freeResources) {
        double tradeGoods = Math.max(0, Math.ceil(choice.ship().cost()) - freeResources);
        return choice.ship().value() + STALL_VALUE - TRADE_GOOD_VALUE * tradeGoods;
    }
}
