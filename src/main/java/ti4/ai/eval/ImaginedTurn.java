package ti4.ai.eval;

import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.SearchMemo;
import ti4.game.Player;

@UtilityClass
public class ImaginedTurn {

    private static final ThreadLocal<Player> IMAGINED = new ThreadLocal<>();

    public static boolean underway() {
        return IMAGINED.get() != null;
    }

    public static boolean imagines(Player seat) {
        return IMAGINED.get() == seat;
    }

    public static <T> T of(Player seat, Supplier<T> turn) {
        @Nullable Player before = IMAGINED.get();
        IMAGINED.set(seat);
        try (DecisionMemo.Open decision = DecisionMemo.open();
                SearchMemo.Open search = SearchMemo.open()) {
            return turn.get();
        } finally {
            if (before == null) {
                IMAGINED.remove();
            } else {
                IMAGINED.set(before);
            }
        }
    }

    public static Map<String, Integer> unscoredSecrets(Player seat) {
        return imagines(seat) ? Map.of() : seat.getSecretsUnscored();
    }

    public static Map<String, Integer> actionCards(Player seat) {
        return imagines(seat) ? Map.of() : seat.getActionCards();
    }
}
