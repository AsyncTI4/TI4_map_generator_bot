package ti4.ai.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class FreelancersRules {

    record Option(String handler, double value, int need) {}

    record Choice(Option option, FreelancersPayment.Quote quote) {
        double net() {
            return option.value() - quote.cost();
        }
    }

    static final double FORWARD_SHIP_BONUS = 1.0;
    static final double MIN_NET_VALUE = 0.25;
    private static final String PLAN_KEY = "freelancers|";
    private static final String BUILD_PREFIX = "freelancersBuild_";
    private static final String DECLINE = "decline_explore";
    private static final String PLACE_PREFIX = "placeOneNDone_dontskipfreelancers_";
    private static final String DONE_PREFIX = "deleteButtons_";
    private static final String SEPARATOR = "_";

    public static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> prompts) {
        return offer(context, prompts).or(() -> place(context, prompts));
    }

    private static Optional<AiDecision> offer(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> build = prompt.firstEnabled(
                    button -> button.isUnowned() && button.handlerId().startsWith(BUILD_PREFIX));
            Optional<PromptButton> decline = prompt.enabledHandler(DECLINE);
            if (build.isEmpty()
                    || decline.isEmpty()
                    || !ExploreWindow.isOwn(context, prompt)
                    || !ExploreWindow.untouched(context, prompt)) {
                continue;
            }
            Tile tile = context.game()
                    .getTileFromPlanet(StringUtils.removeStart(build.get().handlerId(), BUILD_PREFIX));
            Optional<Choice> choice = tile == null ? Optional.empty() : best(context, tile);
            if (choice.isEmpty()) {
                return Optional.of(AiDecision.press(prompt, decline.get(), "decline Freelancers: nothing is worth it"));
            }
            context.memory()
                    .put(PLAN_KEY + context.turnKey(), choice.get().option().handler());
            return Optional.of(
                    AiDecision.press(prompt, build.get(), "produce " + describe(choice.get()) + " with Freelancers"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> place(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            List<PromptButton> units = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(PLACE_PREFIX)
                            && !context.alreadyPressed(prompt, button))
                    .toList();
            Optional<AiDecision> decision = place(context, prompt, units);
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> place(AiTurnContext context, AiPrompt prompt, List<PromptButton> units) {
        Optional<Tile> tile = units.stream()
                .map(button -> tileOf(context.game(), button))
                .flatMap(Optional::stream)
                .findFirst();
        if (tile.isEmpty()) return Optional.empty();
        List<Choice> choices = choices(context, tile.get());
        String planned = context.memory().get(PLAN_KEY + context.turnKey()).orElse("");
        Optional<Choice> chosen = choices.stream()
                .filter(choice -> choice.option().handler().equals(planned))
                .findFirst()
                .or(() -> bestOf(choices));
        Optional<PromptButton> button = chosen.flatMap(choice -> units.stream()
                .filter(candidate -> candidate
                        .handlerId()
                        .equals(PLACE_PREFIX + choice.option().handler()))
                .findFirst());
        if (button.isEmpty()) return Optional.empty();
        context.memory().remove(PLAN_KEY + context.turnKey());
        PaymentRules.expect(
                context,
                "Freelancers",
                chosen.get().quote().payment(),
                DONE_PREFIX + button.get().handlerId());
        return Optional.of(
                AiDecision.press(prompt, button.get(), "produce " + describe(chosen.get()) + " with Freelancers"));
    }

    static Optional<Choice> best(AiTurnContext context, Tile tile) {
        return bestOf(choices(context, tile));
    }

    private static Optional<Choice> bestOf(List<Choice> choices) {
        return choices.stream()
                .filter(choice -> choice.net() >= MIN_NET_VALUE)
                .max(Comparator.comparingDouble(Choice::net));
    }

    static List<Choice> choices(AiTurnContext context, Tile tile) {
        ExploreOutlook outlook = new ExploreOutlook(context.game(), context.seat());
        List<Choice> choices = new ArrayList<>();
        for (Option option : options(context.game(), context.seat(), tile)) {
            FreelancersPayment.cheapest(outlook, option.need())
                    .ifPresent(quote -> choices.add(new Choice(option, quote)));
        }
        return choices;
    }

    private static List<Option> options(Game game, Player seat, Tile tile) {
        List<Option> options = new ArrayList<>();
        boolean forward = !hasDock(seat, tile);
        for (BuildOrder ship :
                ProductionPlanner.oneShipOptions(game, seat, tile, FreelancersPayment.capacity(game, seat))) {
            double value = ship.value() + (forward ? FORWARD_SHIP_BONUS : 0);
            options.add(new Option(ship.unitId() + SEPARATOR + tile.getPosition(), value, need(ship.cost())));
        }
        addFighter(game, seat, tile, options);
        for (Planet planet : tile.getPlanetUnitHolders()) {
            boolean placeable = seat.getPlanets().contains(planet.getName()) && !ExploreSite.isDemilitarized(planet);
            if (placeable) addGroundForces(game, seat, planet, options);
        }
        return options;
    }

    private static void addFighter(Game game, Player seat, Tile tile, List<Option> options) {
        if (fighterRoom(seat, tile) <= 0 || !ExploreValues.hasRoomForUnit(game, seat, UnitType.Fighter)) return;
        double cost = costOf(seat, UnitType.Fighter);
        options.add(new Option(
                "fighter" + SEPARATOR + tile.getPosition(),
                ProductionPlanner.fillerValuePerResource(game) * cost,
                need(cost)));
    }

    private static void addGroundForces(Game game, Player seat, Planet planet, List<Option> options) {
        boolean empty = BoardView.groundForces(planet, seat) == 0;
        double garrison = empty ? ExploreValues.MECH_GARRISON : 0;
        double perResource = ProductionPlanner.fillerValuePerResource(game);
        double infantry = costOf(seat, UnitType.Infantry);
        options.add(new Option(
                "infantry" + SEPARATOR + planet.getName(), perResource * infantry + garrison, need(infantry)));
        double mech = costOf(seat, UnitType.Mech);
        if (!game.isBaseGameMode() && mech > 0 && ExploreValues.hasRoomForUnit(game, seat, UnitType.Mech)) {
            options.add(new Option("mech" + SEPARATOR + planet.getName(), perResource * mech + garrison, need(mech)));
        }
    }

    private static int fighterRoom(Player seat, Tile tile) {
        var space = BoardView.space(tile);
        int capacity = BoardView.MOVING_SHIPS.stream()
                .mapToInt(type -> BoardView.count(space, seat, type) * BoardView.capacity(seat, type))
                .sum();
        int allowance = hasDock(seat, tile) ? BoardView.DOCK_FIGHTER_ALLOWANCE : 0;
        return capacity
                + allowance
                - BoardView.count(space, seat, UnitType.Fighter)
                - BoardView.groundForces(space, seat);
    }

    private static boolean hasDock(Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0);
    }

    private static double costOf(Player seat, UnitType type) {
        return BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
    }

    private static int need(double cost) {
        return (int) Math.ceil(cost);
    }

    private static Optional<Tile> tileOf(Game game, PromptButton button) {
        String location =
                StringUtils.substringAfter(StringUtils.removeStart(button.handlerId(), PLACE_PREFIX), SEPARATOR);
        Tile tile = game.getTileByPosition(location);
        return Optional.ofNullable(tile != null ? tile : game.getTileFromPlanet(location));
    }

    private static String describe(Choice choice) {
        return StringUtils.substringBefore(choice.option().handler(), SEPARATOR);
    }
}
