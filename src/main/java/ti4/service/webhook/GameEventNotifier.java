package ti4.service.webhook;

import ti4.game.Game;
import ti4.game.Player;

public interface GameEventNotifier {
    void notifyActivePlayerChanged(Game game, Player previousActivePlayer, Player activePlayer);

    void notifyPhaseChanged(Game game, String previousPhaseOfGame, String currentPhaseOfGame);

    void notifyAgendaVotingStarted(Game game);

    void notifyAgendaResolved(Game game, String winner);

    void notifyPlayerPassed(Game game, Player passedPlayer, boolean autoPass);

    void notifyGameEnded(Game game);
}
