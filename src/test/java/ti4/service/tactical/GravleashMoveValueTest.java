package ti4.service.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Gravleash Maneuvers (letnevbt): "During movement, your non-fighter ships' move values are equal to the
// highest move value amongst moving ships in the system they started in." Rulings in docs/rules/factions/letnev.md.
class GravleashMoveValueTest extends BaseTi4Test {

    private Game game;
    private Player letnev;
    private Tile origin;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("gravleash-move-value");
        letnev = game.addPlayer("letnevId", "Letnev");
        letnev.setFaction(game, "letnev");
        letnev.setColor("red");
        letnev.addBreakthrough("letnevbt");
        letnev.setBreakthroughUnlocked("letnevbt", true);

        Tile activeSystem = new Tile("20", "000");
        game.setTile(activeSystem);
        game.setActiveSystem("000");
        origin = new Tile("19", "101");
        game.setTile(origin);
    }

    @Test
    void slowShipMatchesFastestShipFromSameSystem() {
        owns("cruiser", "dreadnought");

        assertThat(moveValue(UnitType.Dreadnought, UnitType.Cruiser, UnitType.Dreadnought))
                .isEqualTo(2);
    }

    @Test
    void withoutUnlockedBreakthroughShipsKeepTheirOwnMoveValue() {
        owns("cruiser", "dreadnought");
        letnev.setBreakthroughUnlocked("letnevbt", false);

        assertThat(moveValue(UnitType.Dreadnought, UnitType.Cruiser, UnitType.Dreadnought))
                .isEqualTo(1);
    }

    @Test
    void ruling1_otherShipsMatchThePaceSettersModifiedMoveValue() {
        // Echo of Ascension gets +1 move only for itself, so the pace is its boosted 2, not its printed 1.
        owns("tf-echoofascension", "dreadnought");

        assertThat(moveValue(UnitType.Flagship, UnitType.Flagship, UnitType.Dreadnought))
                .isEqualTo(2);
        assertThat(moveValue(UnitType.Dreadnought, UnitType.Flagship, UnitType.Dreadnought))
                .isEqualTo(2);
    }

    @Test
    void fleetWideBonusIsNotCountedTwice() {
        owns("cruiser", "dreadnought");
        game.setStoredValue("flankspeedBoost", "1");

        assertThat(moveValue(UnitType.Cruiser, UnitType.Cruiser, UnitType.Dreadnought))
                .isEqualTo(3);
        assertThat(moveValue(UnitType.Dreadnought, UnitType.Cruiser, UnitType.Dreadnought))
                .isEqualTo(3);
    }

    @Test
    void ruling2_fighterIISetsThePace() {
        owns("fighter2", "carrier");

        assertThat(moveValue(UnitType.Carrier, UnitType.Fighter, UnitType.Carrier))
                .isEqualTo(2);
    }

    @Test
    void fightersAreNotRaised() {
        owns("fighter2", "cruiser2");

        assertThat(moveValue(UnitType.Fighter, UnitType.Fighter, UnitType.Cruiser))
                .isEqualTo(2);
    }

    @Test
    void nonShipsDoNotSetThePace() {
        // A Floating Factory II (move 2) is a structure, not a ship.
        owns("saar_spacedock2", "dreadnought", "infantry");

        assertThat(moveValue(UnitType.Dreadnought, UnitType.Spacedock, UnitType.Dreadnought, UnitType.Infantry))
                .isEqualTo(1);
    }

    @Test
    void nonShipsAreNotRaised() {
        owns("saar_spacedock", "cruiser", "infantry");

        assertThat(moveValue(UnitType.Spacedock, UnitType.Spacedock, UnitType.Cruiser))
                .isEqualTo(1);
        assertThat(moveValue(UnitType.Infantry, UnitType.Infantry, UnitType.Cruiser))
                .isZero();
    }

    @Test
    void onlyShipsFromTheSameSystemSetThePace() {
        owns("cruiser", "dreadnought");
        Tile twoAway = new Tile("21", "201");
        game.setTile(twoAway);
        displace(origin, UnitType.Cruiser);
        displace(twoAway, UnitType.Dreadnought);

        assertThat(TacticalActionOutputService.buildMessageForSingleSystem(game, letnev, twoAway))
                .contains("distance exceeds move value (2 > 1)");
    }

    @Test
    void exceedsCheckUsesTheLeashedMoveValue() {
        owns("cruiser", "dreadnought");
        Tile twoAway = new Tile("21", "201");
        game.setTile(twoAway);
        displace(twoAway, UnitType.Cruiser, UnitType.Dreadnought);

        assertThat(TacticalActionOutputService.buildMessageForSingleSystem(game, letnev, twoAway))
                .doesNotContain("distance exceeds move value");
    }

    private void owns(String... unitIds) {
        letnev.setUnitsOwned(new HashSet<>(List.of(unitIds)));
    }

    private int moveValue(UnitType unit, UnitType... movingFromOrigin) {
        Set<UnitKey> moving = new HashSet<>();
        for (UnitType type : movingFromOrigin) moving.add(key(type));
        return TacticalActionOutputService.getUnitMoveValue(game, letnev, origin, key(unit), moving);
    }

    private void displace(Tile tile, UnitType... units) {
        Map<UnitKey, List<Integer>> unitStates = new HashMap<>();
        for (UnitType type : units) unitStates.put(key(type), new ArrayList<>(List.of(1, 0, 0, 0)));
        game.getTacticalActionDisplacement().put(tile.getPosition() + "-space", unitStates);
    }

    private UnitKey key(UnitType type) {
        return Units.getUnitKey(type, letnev.getColorID());
    }
}
