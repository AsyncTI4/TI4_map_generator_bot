package ti4.ai.sardakk;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.sardakk.CommanderReach.Garrison;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class SardakkCommanderRules {

    static final double EXTRA_MIN_WIN = 0.3;
    static final double EXTRA_MAX_WIN = 0.85;
    static final double EXTRA_MIN_GAIN = 0.08;
    private static final String USED_KEY = "sardakkCommanderUsed|";
    private static final String HAZARDOUS = "hazardous";
    private static final String DONE_LANDING = "doneLanding_";
    private static final int OFFER_PARTS = 4;
    private static final String MECH = "mech";

    record Offer(Match match, String kind, String destination, String source) {

        UnitType type() {
            return MECH.equals(kind) ? UnitType.Mech : UnitType.Infantry;
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!context.isActivePlayer() || !"action".equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        if (!CommanderReach.unlocked(game, seat) || !TacticalRules.inProgress(context)) return Optional.empty();
        Tile tile = game.getTileByPosition(game.getCurrentActiveSystem());
        if (tile == null || spaceCombatPending(game, seat, tile)) return Optional.empty();
        List<AiPrompt> turn = Prompts.thisTurn(context);
        if (Prompts.owned(turn, context.faction(), (DONE_LANDING + tile.getPosition())::equals)
                .isEmpty()) {
            return Optional.empty();
        }
        Optional<TacticalPlan> plan = TacticalRules.plan(context, tile.getPosition());
        if (plan.isEmpty()) return Optional.empty();
        List<Offer> offers = offers(context, turn, tile);
        if (offers.isEmpty()) return Optional.empty();
        return plannedPull(context, tile, plan.get(), offers)
                .or(() -> reinforcement(context, tile, plan.get(), offers));
    }

    private static boolean spaceCombatPending(Game game, Player seat, Tile tile) {
        return BoardView.hasOwnShips(seat, tile) && BoardView.hasEnemyShips(game, seat, tile);
    }

    private static List<Offer> offers(AiTurnContext context, List<AiPrompt> turn, Tile target) {
        Garrison garrison = TacticalPlanner.garrison(context.game(), context.seat());
        String used = used(context);
        return turn.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> CommanderReach.isButton(button.handlerId()))
                        .filter(button -> !context.alreadyPressed(prompt, button))
                        .map(button -> parse(new Match(prompt, button))))
                .flatMap(Optional::stream)
                .filter(offer -> !used.contains(marker(offer.source())))
                .filter(offer -> sourceCanSpare(context, target, offer, garrison))
                .toList();
    }

    private static Optional<Offer> parse(Match match) {
        String[] parts = match.button().handlerId().split("_");
        if (parts.length != OFFER_PARTS) return Optional.empty();
        return Optional.of(new Offer(match, parts[1], parts[2], parts[3]));
    }

    private static boolean sourceCanSpare(AiTurnContext context, Tile target, Offer offer, Garrison garrison) {
        Game game = context.game();
        Tile source = game.getTileFromPlanet(offer.source());
        if (source == null
                || source == target
                || !(source.getUnitHolders().get(offer.source()) instanceof Planet planet)) {
            return false;
        }
        return CommanderReach.canSpare(game, context.seat(), source, planet, garrison, offer.type());
    }

    private static Optional<AiDecision> plannedPull(
            AiTurnContext context, Tile tile, TacticalPlan plan, List<Offer> offers) {
        int inSpace = BoardView.groundForces(BoardView.space(tile), context.seat());
        for (Map.Entry<String, Integer> wanted : plan.landings().entrySet()) {
            if (!(tile.getUnitHolders().get(wanted.getKey()) instanceof Planet planet)
                    || BoardView.hasCustodians(planet)) {
                continue;
            }
            int missing = wanted.getValue() - BoardView.groundForces(planet, context.seat());
            int fromSpace = Math.max(0, Math.min(inSpace, missing));
            inSpace -= fromSpace;
            if (missing - fromSpace <= 0) continue;
            Optional<Offer> offer = best(context, planet, offers);
            if (offer.isPresent())
                return Optional.of(press(context, offer.get(), "fill the invasion of " + planet.getName()));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> reinforcement(
            AiTurnContext context, Tile tile, TacticalPlan plan, List<Offer> offers) {
        Player seat = context.seat();
        int inSpace = BoardView.groundForces(BoardView.space(tile), seat);
        for (Map.Entry<String, Integer> wanted : plan.landings().entrySet()) {
            if (!(tile.getUnitHolders().get(wanted.getKey()) instanceof Planet planet)
                    || BoardView.hasCustodians(planet)
                    || !BoardView.enemyGroundForcesOn(context.game(), seat, planet)) {
                continue;
            }
            int present = BoardView.groundForces(planet, seat);
            int fromSpace = Math.max(0, Math.min(inSpace, wanted.getValue() - present));
            inSpace -= fromSpace;
            Optional<Offer> offer = best(context, planet, offers);
            if (offer.isPresent() && improvesTheOdds(context, tile, planet, fromSpace, offer.get())) {
                return Optional.of(press(context, offer.get(), "add to the invasion of " + planet.getName()));
            }
        }
        return Optional.empty();
    }

    private static boolean improvesTheOdds(
            AiTurnContext context, Tile tile, Planet planet, int fromSpace, Offer offer) {
        Map<UnitType, Integer> landing = spaceForces(context.seat(), tile, fromSpace);
        double now = CombatForecast.landingWin(
                context.game(), context.seat(), tile.getPosition(), planet.getName(), landing);
        if (now < EXTRA_MIN_WIN || now >= EXTRA_MAX_WIN) return false;
        landing.merge(offer.type(), 1, Integer::sum);
        double with = CombatForecast.landingWin(
                context.game(), context.seat(), tile.getPosition(), planet.getName(), landing);
        return with - now >= EXTRA_MIN_GAIN;
    }

    static Map<UnitType, Integer> spaceForces(Player seat, Tile tile, int count) {
        Map<UnitType, Integer> landing = new EnumMap<>(UnitType.class);
        int left = count;
        for (UnitType type : List.of(UnitType.Infantry, UnitType.Mech)) {
            int taken = Math.min(left, BoardView.count(BoardView.space(tile), seat, type));
            if (taken > 0) landing.put(type, taken);
            left -= taken;
        }
        return landing;
    }

    private static Optional<Offer> best(AiTurnContext context, Planet destination, List<Offer> offers) {
        boolean hazardous = destination.getPlanetTypes().contains(HAZARDOUS);
        return offers.stream()
                .filter(offer -> offer.destination().equals(destination.getName()))
                .min(Comparator.comparingInt((Offer offer) -> hazardous == MECH.equals(offer.kind()) ? 0 : 1)
                        .thenComparingInt(offer -> -groundForcesOn(context, offer.source())));
    }

    private static int groundForcesOn(AiTurnContext context, String sourcePlanet) {
        Tile source = context.game().getTileFromPlanet(sourcePlanet);
        return source == null
                ? 0
                : BoardView.groundForces(source.getUnitHolders().get(sourcePlanet), context.seat());
    }

    private static AiDecision press(AiTurnContext context, Offer offer, String reason) {
        String key = USED_KEY + TacticalRules.actionKey(context);
        context.memory().put(key, used(context) + marker(offer.source()));
        return offer.match().press(reason + " with G'hom Sek'kus from " + offer.source());
    }

    private static String used(AiTurnContext context) {
        return context.memory().get(USED_KEY + TacticalRules.actionKey(context)).orElse("");
    }

    private static String marker(String sourcePlanet) {
        return "|" + StringUtils.trimToEmpty(sourcePlanet) + "|";
    }
}
