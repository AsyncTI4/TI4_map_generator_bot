package ti4.ai.naalu;

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
import ti4.ai.eval.PlanetStake;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.StrategyTokenCost;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatForecast.SpaceOutlook;
import ti4.ai.tactical.CombatRules;
import ti4.ai.tactical.RetreatRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ForesightRules {

    static final String ABILITY = "foresight";
    static final double ESCAPE_BELOW = 0.5;
    private static final String RETREAT_PREFIX = "retreat_";
    private static final String FORESIGHT_SUFFIX = "_foresight";
    private static final String DESTINATION_PREFIX = "retreatUnitsFrom_";
    private static final String SKILLED_SUFFIX = "_skilled";
    private static final String KEY = "foresight|";
    private static final String USED = "used";
    private static final String DECLINED = "declined";
    private static final String DESTINATION_CHOSEN = "moved";

    record Escape(double savedFleet, double stakeKept, double tokenCost, double lockLoss) {

        boolean worthIt(double win) {
            return win < ESCAPE_BELOW && savedFleet - stakeKept > tokenCost + lockLoss;
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!context.seat().hasAbility(ABILITY)) return Optional.empty();
        return chooseDestination(context).or(() -> decide(context));
    }

    private static Optional<AiDecision> chooseDestination(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            List<Match> destinations = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(DESTINATION_PREFIX)
                            && button.handlerId().endsWith(SKILLED_SUFFIX)
                            && !context.alreadyPressed(prompt, button))
                    .map(button -> new Match(prompt, button))
                    .toList();
            if (destinations.isEmpty()) continue;
            String origin = part(destinations.getFirst(), 0);
            String key = key(game, origin);
            if (!context.memory().get(key).filter(USED::equals).isPresent()) continue;
            Optional<Match> best = destinations.stream()
                    .max(Comparator.comparingDouble(
                            match -> RetreatRules.skilledDestinationValue(game, seat, part(match, 1), List.of())));
            if (best.isEmpty()) continue;
            context.memory().put(key, DESTINATION_CHOSEN);
            return best.map(match -> match.press("escape with Foresight to " + part(match, 1)));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> decide(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (context.isActivePlayer() || seat.getStrategicCC() <= 0) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<Match> button = prompt.firstEnabled(candidate -> candidate.isOwnedBy(context.faction())
                            && candidate.handlerId().startsWith(RETREAT_PREFIX)
                            && candidate.handlerId().endsWith(FORESIGHT_SUFFIX)
                            && !context.alreadyPressed(prompt, candidate))
                    .map(found -> new Match(prompt, found));
            if (button.isEmpty()) continue;
            String position = StringUtils.substringBetween(button.get().button().handlerId(), RETREAT_PREFIX, "_");
            String key = key(game, position);
            if (context.memory().has(key) || !beforeFirstRound(game, position)) continue;
            boolean escape = shouldEscape(game, seat, position);
            context.memory().put(key, escape ? USED : DECLINED);
            if (escape) return Optional.of(button.get().press("escape a losing space combat with Foresight"));
        }
        return Optional.empty();
    }

    static boolean shouldEscape(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return false;
        Optional<SpaceOutlook> outlook = CombatForecast.spaceOutlook(game, seat, position);
        if (outlook.isEmpty()) return false;
        return escape(game, seat, tile, outlook.get())
                .filter(escape -> escape.worthIt(outlook.get().win()))
                .isPresent();
    }

    static Optional<Escape> escape(Game game, Player seat, Tile tile, SpaceOutlook outlook) {
        List<Tile> destinations = RetreatRules.skilledRetreatDestinations(game, seat, tile);
        if (destinations.isEmpty()) return Optional.empty();
        double lockLoss = destinations.stream()
                .mapToDouble(destination -> CommandTokenValue.lockLoss(game, seat, destination, List.of()))
                .min()
                .orElse(0);
        double tokenCost = StrategyTokenCost.toSpend(game, seat).orElse(StrategyTokenCost.UNSPARED);
        return Optional.of(new Escape(outlook.ourLoss(), outlook.win() * stake(game, seat, tile), tokenCost, lockLoss));
    }

    private static double stake(Game game, Player seat, Tile tile) {
        double stake = 0;
        for (Planet planet : BoardView.planets(tile)) {
            if (!seat.getPlanets().contains(planet.getName())) continue;
            stake += PlanetStake.of(game, seat, planet) + PlanetStake.structures(planet, seat);
        }
        return stake;
    }

    private static boolean beforeFirstRound(Game game, String position) {
        return game.getRealPlayers().stream()
                .allMatch(player -> CombatRules.tracker(game, player.getFaction(), position, BoardView.SPACE) == 0);
    }

    private static String part(Match match, int index) {
        String[] parts = StringUtils.removeStart(match.button().handlerId(), DESTINATION_PREFIX)
                .split("_");
        return parts.length > index ? parts[index] : "";
    }

    private static String key(Game game, String position) {
        return KEY + game.getRound() + "|" + position + "|" + game.getActivePlayerID();
    }
}
