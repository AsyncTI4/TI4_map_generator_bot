package ti4.service.relic;

import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

public final class TriadService {

    public static void checkAndUpdateTriad(Game game) {
        for (Player player : game.getPlayers().values()) {
            if (player.hasRelic("thetriad")) {
                if (!player.hasPlanet("triad")) player.addPlanet("triad");
                Planet triad = game.getPlanet("triad");
                if (triad != null) triad.updateTriadStats(player);
            } else {
                player.removePlanet("triad");
            }
            if (player.hasUnlockedBreakthrough("khraskbt")) {
                if (!player.hasPlanet("grove")) player.addPlanet("grove");
                Planet grove = game.getPlanet("grove");
                if (grove != null) grove.updateGroveStats(player);
            } else {
                player.removePlanet("grove");
            }
        }
    }
}
