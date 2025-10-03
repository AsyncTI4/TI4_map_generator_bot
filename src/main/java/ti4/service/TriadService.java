package ti4.service;

import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;

public class TriadService {

    public static void checkAndUpdateTriad(Game game) {
        for (Player player : game.getPlayers().values()) {
            if (player.hasRelic("thetriad")) {
                if (!player.hasPlanet("triad")) player.addPlanet("triad");
                Planet triad = game.getPlanetsInfo().get("triad");
                if (triad != null) triad.updateTriadStats(player);
            } else {
                player.removePlanet("triad");
            }
        }
    }
}
