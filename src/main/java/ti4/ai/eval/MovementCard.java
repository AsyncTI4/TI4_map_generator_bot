package ti4.ai.eval;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;

public enum MovementCard {
    FLANK_SPEED(MoveBoost.FLANK_SPEED, null),
    LOST_STAR_CHART("lost_star", MoveEnablers.Enabler.LOST_STAR_CHART),
    NAV_SUITE("nav_suite", MoveEnablers.Enabler.NAV_SUITE);

    private final String automationId;
    private final MoveEnablers.Enabler enabler;

    MovementCard(String automationId, MoveEnablers.Enabler enabler) {
        this.automationId = automationId;
        this.enabler = enabler;
    }

    public String automationId() {
        return automationId;
    }

    public boolean isCard(String alias) {
        ActionCardModel model = Mapper.getActionCard(alias);
        return model != null && automationId.equals(model.getAutomationID());
    }

    public static Optional<MovementCard> of(String alias) {
        for (MovementCard card : values()) {
            if (card.isCard(alias)) return Optional.of(card);
        }
        return Optional.empty();
    }

    public Optional<String> held(Player seat) {
        return ImaginedTurn.actionCards(seat).keySet().stream()
                .filter(this::isCard)
                .sorted()
                .findFirst();
    }

    public boolean played(Game game, Player seat) {
        return enabler == null
                ? !game.getStoredValue(ShipSpeed.FLANK_SPEED_BOOST).isEmpty()
                : MoveEnablers.played(game, seat, enabler);
    }

    public boolean assumed(Player seat) {
        return enabler == null ? MoveBoost.active(seat) : MoveEnablers.assumed(seat, enabler);
    }

    public <T> T assuming(Player seat, Supplier<T> evaluation) {
        return enabler == null
                ? MoveBoost.withOneMore(seat, evaluation)
                : MoveEnablers.assuming(seat, enabler, evaluation);
    }

    public static <T> T assumingAll(Player seat, List<MovementCard> cards, Supplier<T> evaluation) {
        Supplier<T> wrapped = evaluation;
        for (MovementCard card : cards) {
            Supplier<T> inner = wrapped;
            wrapped = () -> card.assuming(seat, inner);
        }
        return wrapped.get();
    }

    public static boolean anyAssumed(Player seat) {
        for (MovementCard card : values()) {
            if (card.assumed(seat)) return true;
        }
        return false;
    }
}
