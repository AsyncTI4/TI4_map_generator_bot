package ti4.ai.winnu;

import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;

// A Winnu seat (the second AI id) beside the test game's Nekro and Sol seats, with Mecatol Rex (tile 18) at the centre
// of the map. Nothing is placed for the Winnu: each test builds what it needs.
final class WinnuTestBoard {

    static final String MECATOL_POSITION = "000";
    static final String MECATOL = "mr";

    final AiTestGame test = new AiTestGame();
    final Player winnu;
    final Tile mecatol;

    WinnuTestBoard() {
        winnu = test.addSeat(AiTestGame.SECOND_AI_ID, "winnu", "green");
        mecatol = test.place("18", MECATOL_POSITION);
    }

    void winnuActive(String phase) {
        test.isActive(winnu, phase);
    }

    void unlockLeader(String leader) {
        if (winnu.unsafeGetLeader(leader) == null) winnu.addLeader(leader);
        winnu.unsafeGetLeader(leader).setLocked(false);
    }

    void holdMecatol() {
        winnu.addPlanet(MECATOL);
    }
}
