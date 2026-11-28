package ti4.ai.fallback;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.nekro.CommandTokenPolicy.Pool;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class RedistributionGuard {

    public record Finish(boolean pressed, int restored) {}

    private static final String FINISH_HANDLER = "deleteButtons";
    private static final List<String> FINISH_LABELS = List.of("Done Redistributing", "Done Gaining Command Tokens");
    private static final String STARTING_ALLOCATION_KEY = "originalCCsFor";
    private static final int MAX_RESTORES = 20;

    public static boolean finishesRedistribution(PromptButton button) {
        return FINISH_HANDLER.equals(button.handlerId())
                && FINISH_LABELS.stream().anyMatch(label -> button.label().startsWith(label));
    }

    public static int missingTokens(Game game, Player seat) {
        return startingAllocation(game, seat)
                .map(start -> start[0] + start[1] + start[2] - tokensHeld(seat))
                .filter(missing -> missing > 0)
                .orElse(0);
    }

    public static Finish pressRestoringTokens(
            Supplier<Game> game, String seatId, AiPrompt prompt, PromptButton choice, Predicate<PromptButton> press) {
        if (!finishesRedistribution(choice)) return new Finish(press.test(choice), 0);
        int restored = 0;
        while (missingTokens(game.get(), game.get().getPlayer(seatId)) > 0) {
            Optional<PromptButton> add = restoreButton(game.get(), game.get().getPlayer(seatId), prompt);
            if (add.isEmpty() || restored == MAX_RESTORES || !press.test(add.get())) return new Finish(false, restored);
            restored++;
        }
        return new Finish(press.test(choice), restored);
    }

    static Optional<PromptButton> restoreButton(Game game, Player seat, AiPrompt prompt) {
        Optional<int[]> start = startingAllocation(game, seat);
        if (start.isPresent()) {
            for (Pool pool : Pool.values()) {
                if (heldIn(seat, pool) >= startingIn(start.get(), pool)) continue;
                Optional<PromptButton> button = prompt.enabledHandler(pool.grow());
                if (button.isPresent()) return button;
            }
        }
        return prompt.enabledHandler(Pool.TACTIC.grow());
    }

    private static Optional<int[]> startingAllocation(Game game, Player seat) {
        String stored = game.getStoredValue(STARTING_ALLOCATION_KEY + seat.getFaction());
        String[] parts = StringUtils.split(stored, '/');
        if (parts.length != 3 || !List.of(parts).stream().allMatch(StringUtils::isNumeric)) return Optional.empty();
        return Optional.of(
                new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
    }

    private static int tokensHeld(Player seat) {
        return seat.getTacticalCC() + seat.getFleetCC() + seat.getStrategicCC();
    }

    private static int heldIn(Player seat, Pool pool) {
        return switch (pool) {
            case TACTIC -> seat.getTacticalCC();
            case FLEET -> seat.getFleetCC();
            case STRATEGY -> seat.getStrategicCC();
        };
    }

    private static int startingIn(int[] start, Pool pool) {
        return switch (pool) {
            case TACTIC -> start[0];
            case FLEET -> start[1];
            case STRATEGY -> start[2];
        };
    }
}
