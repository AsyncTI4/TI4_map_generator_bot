package ti4.ai.agenda;

import ti4.game.Game;
import ti4.game.Player;

record AgendaView(Game game, Player voter, boolean ownView) {

    boolean readsHandOf(Player holder) {
        return ownView && holder == voter;
    }
}
