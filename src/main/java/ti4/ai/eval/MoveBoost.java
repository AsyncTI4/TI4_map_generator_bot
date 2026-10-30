package ti4.ai.eval;

import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;

@UtilityClass
public class MoveBoost {

    public static final String FLANK_SPEED = "flank_speed";
    private static final ThreadLocal<Player> BOOSTED = new ThreadLocal<>();

    public static boolean isFlankSpeed(String alias) {
        ActionCardModel model = Mapper.getActionCard(alias);
        return model != null && FLANK_SPEED.equals(model.getAutomationID());
    }

    public static Optional<String> flankSpeedCard(Player seat) {
        return seat.getActionCards().keySet().stream()
                .filter(MoveBoost::isFlankSpeed)
                .sorted()
                .findFirst();
    }

    public static int extra(Player player) {
        return BOOSTED.get() == player ? 1 : 0;
    }

    public static boolean active(Player player) {
        return extra(player) > 0;
    }

    public static <T> T withOneMore(Player seat, Supplier<T> evaluation) {
        @Nullable Player before = BOOSTED.get();
        BOOSTED.set(seat);
        try {
            return evaluation.get();
        } finally {
            if (before == null) {
                BOOSTED.remove();
            } else {
                BOOSTED.set(before);
            }
        }
    }
}
