package ti4.ai.eval;

import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Player;

@UtilityClass
public class DecidingSeat {

    private static final ThreadLocal<String> DECIDING = new ThreadLocal<>();

    public static boolean knowsHandOf(Player seat) {
        return Objects.equals(DECIDING.get(), seat.getUserID()) && !ImaginedTurn.imagines(seat);
    }

    public static <T> T of(Player seat, Supplier<T> decision) {
        @Nullable String before = DECIDING.get();
        DECIDING.set(seat.getUserID());
        try {
            return decision.get();
        } finally {
            if (before == null) {
                DECIDING.remove();
            } else {
                DECIDING.set(before);
            }
        }
    }
}
