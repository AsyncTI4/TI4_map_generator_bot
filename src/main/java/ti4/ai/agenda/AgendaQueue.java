package ti4.ai.agenda;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class AgendaQueue {

    private static final String QUEUED_WHENS = "queuedWhens";
    private static final String QUEUED_WHEN_OF = "queuedWhensFor";
    private static final String QUEUED_AFTERS = "queuedAfters";
    private static final String QUEUED_AFTER_OF = "queuedAftersFor";

    public static boolean holdsQueuedWhen(Game game, Player seat) {
        return queuedWhen(game, seat).isPresent();
    }

    public static boolean holdsQueuedAfter(Game game, Player seat) {
        return queuedAfter(game, seat).isPresent();
    }

    public static Optional<String> queuedWhen(Game game, Player seat) {
        return entry(game, seat, QUEUED_WHENS, QUEUED_WHEN_OF);
    }

    public static Optional<String> queuedAfter(Game game, Player seat) {
        return entry(game, seat, QUEUED_AFTERS, QUEUED_AFTER_OF);
    }

    private static Optional<String> entry(Game game, Player seat, String queue, String slot) {
        if (!game.getStoredValue(queue).contains(seat.getFaction() + "_")) return Optional.empty();
        String queued = game.getStoredValue(slot + seat.getFaction());
        return queued.isEmpty() ? Optional.empty() : Optional.of(queued);
    }
}
