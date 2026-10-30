package ti4.ai.sardakk;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MecatolStake;
import ti4.ai.eval.PlanetStake;
import ti4.ai.eval.Threats;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.Stall;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class TroAgentRules {

    public static final String USE_PREFIX = "exhaustAgent_sardakkagent_";
    private static final String AGENT = "sardakkagent";
    private static final String PLANETS_TAKEN = "planetsTakenThisRound";
    private static final int INFANTRY_PLACED = 2;
    private static final double GUARD_SHARE = 0.5;
    private static final double OBJECTIVE_SHARE = 0.5;
    private static final double MECATOL_SHARE = 0.5;
    private static final double FRESHLY_TAKEN_EXPOSURE = 0.5;
    private static final double GARRISON_SOFTENING = 2.0;
    static final double WORTH_SPENDING_NOW = 0.5;
    static final int TOKENS_LEFT_IN_THE_LAST_WINDOWS = 1;

    record Offer(Match match, Tile tile, Planet planet) {}

    public static Optional<AiDecision> afterTacticalAction(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasUnexhaustedLeader(AGENT) || !context.isActivePlayer()) return Optional.empty();
        int placed = Math.min(INFANTRY_PLACED, infantryRoom(context.game(), seat));
        if (placed == 0) return Optional.empty();
        List<Offer> offers = offers(context);
        if (offers.isEmpty()) return Optional.empty();
        ObjectiveValue objectives = new ObjectiveValue(context.game(), seat);
        Optional<Offer> best = offers.stream()
                .max(Comparator.comparingDouble((Offer offer) -> special(context, offer, objectives))
                        .thenComparingDouble(offer -> BoardView.planetValue(offer.planet()))
                        .thenComparing(offer -> offer.planet().getName(), Comparator.reverseOrder()));
        if (best.isEmpty()) return Optional.empty();
        double special = special(context, best.get(), objectives);
        if (special < WORTH_SPENDING_NOW && !lastWindowsOfTheRound(context)) return Optional.empty();
        String why = special >= WORTH_SPENDING_NOW ? "to hold" : "before the round runs out";
        return Optional.of(best.get()
                .match()
                .press("use T'ro An to place " + placed + " infantry on "
                        + best.get().planet().getName() + " " + why));
    }

    private static List<Offer> offers(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        return Prompts.thisTurn(context).stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isOwnedBy(seat.getFaction())
                                && button.handlerId().startsWith(USE_PREFIX))
                        .filter(button -> !context.alreadyPressed(prompt, button))
                        .map(button -> offer(game, seat, prompt, button.handlerId(), button)))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<Offer> offer(
            Game game, Player seat, AiPrompt prompt, String handlerId, PromptButton button) {
        String target = StringUtils.removeStart(handlerId, USE_PREFIX);
        String position = StringUtils.substringBefore(target, "_");
        String planetName = StringUtils.substringAfter(target, "_");
        Tile tile = game.getTileByPosition(position);
        if (tile == null || planetName.isEmpty() || !seat.getPlanets().contains(planetName)) return Optional.empty();
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> planet.getName().equals(planetName))
                .findFirst()
                .map(planet -> new Offer(new Match(prompt, button), tile, planet));
    }

    private static double special(AiTurnContext context, Offer offer, ObjectiveValue objectives) {
        Game game = context.game();
        Player seat = context.seat();
        double guard = exposure(context, offer) * GUARD_SHARE * PlanetStake.of(game, seat, offer.planet(), objectives);
        double objective = OBJECTIVE_SHARE * objectiveLoss(objectives, offer.planet());
        double mecatol = offer.tile().isMecatol(game) ? MECATOL_SHARE * MecatolStake.holdValue(game, seat) : 0;
        return guard + objective + mecatol;
    }

    private static double objectiveLoss(ObjectiveValue objectives, Planet planet) {
        return Math.max(0, -objectives.gain(objectives.before().withoutPlanet(planet.getName())));
    }

    private static double exposure(AiTurnContext context, Offer offer) {
        Game game = context.game();
        Player seat = context.seat();
        boolean reachable = Threats.anyEnemyCanReach(game, seat, offer.tile());
        boolean freshlyTaken =
                game.getStoredValue(PLANETS_TAKEN).contains(offer.planet().getName());
        double base = reachable ? 1.0 : freshlyTaken ? FRESHLY_TAKEN_EXPOSURE : 0;
        double garrison = BoardView.groundForces(offer.planet(), seat);
        return base * GARRISON_SOFTENING / (GARRISON_SOFTENING + garrison);
    }

    private static boolean lastWindowsOfTheRound(AiTurnContext context) {
        Player seat = context.seat();
        return seat.getTacticalCC() <= TOKENS_LEFT_IN_THE_LAST_WINDOWS
                || !Stall.othersStillActing(context.game(), seat);
    }

    private static int infantryRoom(Game game, Player seat) {
        String asyncId = BoardView.model(seat, UnitType.Infantry)
                .map(UnitModel::getAsyncId)
                .orElse(null);
        if (asyncId == null) return 0;
        int cap = seat.getUnitCap(asyncId);
        if (cap <= 0) return INFANTRY_PLACED;
        return Math.max(0, cap - ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId));
    }
}
