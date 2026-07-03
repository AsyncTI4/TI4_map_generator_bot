package ti4.service.webhook;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.context.SpringContext;

@UtilityClass
public class GameEventNotifierFacade {
    private static final GameEventNotifier NO_OP_NOTIFIER = new GameEventNotifier() {
        @Override
        public void notifyActivePlayerChanged(Game game, Player previousActivePlayer, Player activePlayer) {}

        @Override
        public void notifyPhaseChanged(Game game, String previousPhaseOfGame, String currentPhaseOfGame) {}

        @Override
        public void notifyAgendaVotingStarted(Game game) {}

        @Override
        public void notifyAgendaResolved(Game game, String winner) {}

        @Override
        public void notifyPlayerPassed(Game game, Player passedPlayer, boolean autoPass) {}

        @Override
        public void notifyGameEnded(Game game) {}
    };

    public static void notifyActivePlayerChanged(Game game, Player previousActivePlayer, Player activePlayer) {
        notifier().notifyActivePlayerChanged(game, previousActivePlayer, activePlayer);
    }

    public static void notifyPhaseChanged(Game game, String previousPhaseOfGame, String currentPhaseOfGame) {
        notifier().notifyPhaseChanged(game, previousPhaseOfGame, currentPhaseOfGame);
    }

    public static void notifyAgendaVotingStarted(Game game) {
        notifier().notifyAgendaVotingStarted(game);
    }

    public static void notifyAgendaResolved(Game game, String winner) {
        notifier().notifyAgendaResolved(game, winner);
    }

    public static void notifyPlayerPassed(Game game, Player passedPlayer, boolean autoPass) {
        notifier().notifyPlayerPassed(game, passedPlayer, autoPass);
    }

    public static void notifyGameEnded(Game game) {
        notifier().notifyGameEnded(game);
    }

    private static GameEventNotifier notifier() {
        try {
            return SpringContext.getBean(GameEventNotifier.class);
        } catch (RuntimeException e) {
            return NO_OP_NOTIFIER;
        }
    }
}
