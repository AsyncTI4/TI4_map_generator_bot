package ti4.ai.yin;

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
import ti4.ai.perception.AiPrompt;
import ti4.ai.scoring.PaymentRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class BrotherOmarRules {

    public static final String STEP_ONE = "yinCommanderStep1_";
    public static final String REMOVAL_PREFIX = "yinCommanderRemoval_";
    private static final String KEY = "brotherOmar|";
    private static final String FIELD = "~";
    private static final String REQUESTED = "requested";
    private static final String OPENED = "opened";
    private static final long WINDOW_MILLIS = 10 * 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final double HOME_BONUS = 0.5;

    public static void expect(AiTurnContext context) {
        context.memory().put(key(context), String.join(FIELD, REQUESTED, String.valueOf(context.now())));
    }

    public static Optional<AiDecision> returnInfantry(AiTurnContext context) {
        Optional<String> state = context.memory().get(key(context));
        if (state.isEmpty()) return Optional.empty();
        String[] fields = StringUtils.splitPreserveAllTokens(state.get(), FIELD);
        if (fields == null || fields.length != 2 || !StringUtils.isNumeric(fields[1])) {
            context.memory().remove(key(context));
            return Optional.empty();
        }
        long since = Long.parseLong(fields[1]);
        if (context.now() - since > WINDOW_MILLIS) {
            context.memory().remove(key(context));
            return Optional.empty();
        }
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<Match> removal = bestRemoval(context, fresh);
        if (removal.isPresent()) {
            context.memory().remove(key(context));
            return Optional.of(removal.get().press("return an infantry to Brother Omar for the prerequisites"));
        }
        if (OPENED.equals(fields[0])) return Optional.empty();
        Optional<Match> step = fresh.stream()
                .filter(BrotherOmarRules::isResearchPayment)
                .map(prompt -> prompt.firstEnabled(button -> button.isUnowned() && STEP_ONE.equals(button.handlerId()))
                        .map(button -> new Match(prompt, button)))
                .flatMap(Optional::stream)
                .findFirst();
        if (step.isEmpty()) return Optional.empty();
        context.memory().put(key(context), String.join(FIELD, OPENED, String.valueOf(since)));
        return Optional.of(step.get().press("use Brother Omar to ignore the technology's prerequisites"));
    }

    private static boolean isResearchPayment(AiPrompt prompt) {
        return prompt.firstEnabled(
                        button -> button.isUnowned() && PaymentRules.TECHNOLOGY_DONE.equals(button.handlerId()))
                .isPresent();
    }

    private static Optional<Match> bestRemoval(AiTurnContext context, List<AiPrompt> fresh) {
        Game game = context.game();
        Player seat = context.seat();
        return fresh.stream()
                .filter(prompt -> Prompts.mentions(prompt, seat))
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button ->
                                button.isUnowned() && button.handlerId().startsWith(REMOVAL_PREFIX))
                        .map(button -> new Match(prompt, button)))
                .max(Comparator.comparingDouble(match -> removalScore(game, seat, match)));
    }

    static double removalScore(Game game, Player seat, Match match) {
        String[] parts = StringUtils.removeStart(match.button().handlerId(), REMOVAL_PREFIX)
                .split("_");
        if (parts.length < 2) return 0;
        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        if (holder == null) return 0;
        double score = BoardView.count(holder, seat, UnitType.Infantry);
        if (BoardView.SPACE.equals(holder.getName())) score -= 1;
        if (tile == seat.getHomeSystemTile()) score += HOME_BONUS;
        return score;
    }

    private static String key(AiTurnContext context) {
        return KEY + context.game().getRound();
    }
}
