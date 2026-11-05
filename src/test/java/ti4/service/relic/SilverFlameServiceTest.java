package ti4.service.relic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class SilverFlameServiceTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player nekro;
    private Tile homeSystem;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("silver-flame-test");
        sol = game.addPlayer("200000000000000001", "Sol");
        sol.setFaction("sol");
        sol.setColor("blue");
        nekro = game.addPlayer("200000000000000002", "Nekro");
        nekro.setFaction("nekro");
        nekro.setColor("black");
        homeSystem = new Tile("01", "101");
    }

    private UnitKey key(Player owner, UnitType type) {
        return Units.getUnitKey(type, owner.getColorID());
    }

    @Test
    void reducesTheCapOfEveryOwnerWithUnitsInTheSystem() {
        sol.setUnitCap("dd", 5);
        nekro.setUnitCap("dd", 3);
        homeSystem.addUnit(Constants.SPACE, key(sol, UnitType.Destroyer), 2);
        homeSystem.addUnit(Constants.SPACE, key(nekro, UnitType.Destroyer), 2);

        SilverFlameService.reduceUnitCaps(game, SilverFlameService.countUnitsToPurge(homeSystem));

        assertThat(sol.getUnitCap("dd")).isEqualTo(3);
        assertThat(nekro.getUnitCap("dd")).isEqualTo(1);
    }

    @Test
    void leavesPlayersWithoutUnitsInTheSystemUntouched() {
        sol.setUnitCap("dd", 5);
        nekro.setUnitCap("dd", 3);
        homeSystem.addUnit(Constants.SPACE, key(sol, UnitType.Destroyer), 2);

        SilverFlameService.reduceUnitCaps(game, SilverFlameService.countUnitsToPurge(homeSystem));

        assertThat(sol.getUnitCap("dd")).isEqualTo(3);
        assertThat(nekro.getUnitCap("dd")).isEqualTo(3);
    }

    @Test
    void countsFightersAndInfantryOncePerPlanetOrSpaceArea() {
        homeSystem.addUnit(Constants.SPACE, key(nekro, UnitType.Fighter), 4);
        String planet = homeSystem.getPlanetUnitHolders().getFirst().getName();
        homeSystem.addUnit(planet, key(nekro, UnitType.Infantry), 3);
        homeSystem.addUnit(Constants.SPACE, key(nekro, UnitType.Destroyer), 2);

        Map<UnitKey, Integer> counts = SilverFlameService.countUnitsToPurge(homeSystem);

        assertThat(counts)
                .containsEntry(key(nekro, UnitType.Fighter), 1)
                .containsEntry(key(nekro, UnitType.Infantry), 1)
                .containsEntry(key(nekro, UnitType.Destroyer), 2);
    }
}
