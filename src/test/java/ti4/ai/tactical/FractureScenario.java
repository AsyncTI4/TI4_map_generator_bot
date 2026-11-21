package ti4.ai.tactical;

import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.service.map.FractureService;

// The Fracture on the defence board: seven fracture tiles with their neutral units, and an ingress token on the system
// next to the Nekro seat's home, so the Fracture is two moves (Cocytus, Lethe and Phlegethon) or three (Styx) away.
final class FractureScenario {

    static final String COCYTUS = "frac1";
    static final String STYX = "frac4";
    static final String LETHE = "frac7";

    final DefenseScenario board = new DefenseScenario();
    final Tile ingress;

    FractureScenario() {
        FractureService.spawnFracture(null, board.test.game);
        ingress = board.test.place("26", board.otherNeighbourOfHome());
        ingress.addToken(Constants.TOKEN_INGRESS, "space");
        board.test.nekro.setFleetCC(12);
    }

    Player nekro() {
        return board.test.nekro;
    }

    Tile fracture(String position) {
        return board.test.game.getTileByPosition(position);
    }

    void ships(String position, UnitType type, int count) {
        board.test.units(fracture(position), "space", nekro(), type, count);
    }

    void ships(Tile tile, UnitType type, int count) {
        board.test.units(tile, "space", nekro(), type, count);
    }
}
